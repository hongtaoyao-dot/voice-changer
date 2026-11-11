package com.example.voicechanger

/**
 * 一个音色预设。所有参数都是"在原声基础上"的变化量。
 *
 * @param semitones      音高偏移（半音）。同时会让共振峰一起移动，听起来声道变"小"（少女/儿童）。
 * @param tempo          语速倍率，<1 更慢（老年感），不影响音高。
 * @param vibratoDepth   音高颤动深度（0.01 约等于 ±1% 的音高抖动），模拟老人嗓音不稳。
 * @param vibratoRateHz  音高颤动频率。
 * @param tremoloDepth   音量颤动深度（0~1）。
 * @param tremoloRateHz  音量颤动频率。
 * @param brightness     高频增减，>0 更明亮、<0 更闷。
 * @param breath         气声/沙哑噪声量，随说话音量起伏。
 */
data class VoiceEffect(
    val id: String,
    val label: String,
    val semitones: Float = 0f,
    val tempo: Float = 1f,
    val vibratoDepth: Float = 0f,
    val vibratoRateHz: Float = 5f,
    val tremoloDepth: Float = 0f,
    val tremoloRateHz: Float = 5f,
    val brightness: Float = 0f,
    val breath: Float = 0f,
) {
    companion object {
        val ORIGINAL = VoiceEffect(id = "original", label = "原声")

        val GIRL = VoiceEffect(
            id = "girl", label = "少女音",
            semitones = 5f, tempo = 1.0f, brightness = 0.4f, breath = 0.01f,
        )

        val YOUNG_WOMAN = VoiceEffect(
            id = "woman", label = "少妇音",
            semitones = 3.5f, tempo = 0.97f, brightness = 0.15f, breath = 0.03f,
            vibratoDepth = 0.003f, vibratoRateHz = 5f,
        )

        val OLD_WOMAN = VoiceEffect(
            id = "granny", label = "老妇音",
            semitones = 2.5f, tempo = 0.88f, brightness = -0.25f, breath = 0.05f,
            vibratoDepth = 0.012f, vibratoRateHz = 5.5f,
            tremoloDepth = 0.12f, tremoloRateHz = 5f,
        )

        val CHILD = VoiceEffect(
            id = "child", label = "儿童音",
            semitones = 8.5f, tempo = 1.05f, brightness = 0.3f,
        )

        val ALL = listOf(ORIGINAL, GIRL, YOUNG_WOMAN, OLD_WOMAN, CHILD)
    }
}
