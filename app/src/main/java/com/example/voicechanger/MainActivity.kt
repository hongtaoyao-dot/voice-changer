package com.example.voicechanger

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val sampleRate = 44100
    private val maxSeconds = 60

    private lateinit var tvStatus: TextView
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var chipGroup: ChipGroup

    private val ui = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private var recorder: PcmRecorder? = null
    private var recording = false
    private var original: ShortArray? = null
    private var processed: ShortArray? = null
    private var selected: VoiceEffect = VoiceEffect.GIRL
    private var jobId = 0
    private var player: MediaPlayer? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording() else toast(R.string.need_mic)
        }

    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
            if (uri != null) saveTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        btnRecord = findViewById(R.id.btnRecord)
        btnPlay = findViewById(R.id.btnPlay)
        btnSave = findViewById(R.id.btnSave)
        chipGroup = findViewById(R.id.chipGroup)

        setupChips()

        btnRecord.setOnClickListener { onRecordClicked() }
        btnPlay.setOnClickListener { processed?.let { playPcm(it) } }
        btnSave.setOnClickListener {
            saveLauncher.launch("voice_${selected.id}_${System.currentTimeMillis()}.wav")
        }
        updateButtons()
    }

    // ------------------------------------------------------------------ UI

    private fun setupChips() {
        var selectedChipId = View.NO_ID
        for (fx in VoiceEffect.ALL) {
            val chip = layoutInflater.inflate(R.layout.item_chip, chipGroup, false) as Chip
            chip.id = View.generateViewId()
            chip.text = fx.label
            chip.tag = fx
            chipGroup.addView(chip)
            if (fx == selected) selectedChipId = chip.id
        }
        chipGroup.check(selectedChipId)
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val fx = group.findViewById<Chip>(id)?.tag as? VoiceEffect
                ?: return@setOnCheckedStateChangeListener
            selected = fx
            convertAndPlay()
        }
    }

    private fun updateButtons() {
        btnRecord.setText(if (recording) R.string.record_stop else R.string.record_start)
        btnPlay.isEnabled = !recording && processed != null
        btnSave.isEnabled = !recording && processed != null
        for (i in 0 until chipGroup.childCount) {
            chipGroup.getChildAt(i).isEnabled = !recording
        }
    }

    private fun toast(@StringRes res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------- 录音

    private fun onRecordClicked() {
        if (recording) {
            recorder?.stop()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        stopPlayback()
        jobId++                       // 作废还在进行的转换
        original = null
        processed = null
        recording = true
        updateButtons()
        tvStatus.text = getString(R.string.recording_time, 0f)

        val r = PcmRecorder(
            sampleRate = sampleRate,
            maxSeconds = maxSeconds,
            onTick = { sec -> ui.post { if (recording) tvStatus.text = getString(R.string.recording_time, sec) } },
            onFinished = { pcm -> ui.post { onRecorded(pcm) } },
            onError = {
                ui.post {
                    recording = false
                    updateButtons()
                    tvStatus.setText(R.string.hint_start)
                    toast(R.string.record_failed)
                }
            },
        )
        recorder = r
        r.start()
    }

    private fun onRecorded(pcm: ShortArray) {
        if (isDestroyed) return
        recording = false
        recorder = null
        if (pcm.size < sampleRate * 3 / 10) {
            updateButtons()
            tvStatus.setText(R.string.hint_start)
            toast(R.string.too_short)
            return
        }
        original = pcm
        updateButtons()
        convertAndPlay()
    }

    // ------------------------------------------------------------- 变声

    private fun convertAndPlay() {
        val src = original ?: return
        if (recording) return
        val fx = selected
        val myJob = ++jobId

        stopPlayback()
        processed = null
        updateButtons()
        tvStatus.setText(R.string.processing)

        worker.execute {
            val out = try {
                VoiceProcessor.process(src, sampleRate, fx)
            } catch (e: Throwable) {
                null
            }
            ui.post {
                if (isDestroyed || myJob != jobId) return@post   // 已被新的操作取代
                if (out == null) {
                    tvStatus.setText(R.string.hint_start)
                    toast(R.string.process_failed)
                    return@post
                }
                processed = out
                tvStatus.text = getString(R.string.ready_time, out.size.toFloat() / sampleRate)
                updateButtons()
                playPcm(out)
            }
        }
    }

    // ------------------------------------------------------------- 播放 / 保存

    private fun playPcm(pcm: ShortArray) {
        stopPlayback()
        try {
            val file = File(cacheDir, "effect.wav")
            WavIO.write(file, pcm, sampleRate)
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopPlayback()
            toast(R.string.play_failed)
        }
    }

    private fun stopPlayback() {
        player?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // 播放器尚未开始，忽略
            }
            it.release()
        }
        player = null
    }

    private fun saveTo(uri: Uri) {
        val pcm = processed ?: return
        worker.execute {
            val ok = try {
                val stream = contentResolver.openOutputStream(uri)
                if (stream == null) {
                    false
                } else {
                    stream.use { WavIO.writeTo(it, pcm, sampleRate) }
                    true
                }
            } catch (e: Exception) {
                false
            }
            ui.post { if (!isDestroyed) toast(if (ok) R.string.saved else R.string.save_failed) }
        }
    }

    // ------------------------------------------------------------- 生命周期

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.stop()
        stopPlayback()
        worker.shutdown()
    }
}
