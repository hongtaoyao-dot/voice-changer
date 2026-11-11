# 变声器（Android）

点录音说一段话，点选音色，立刻播放效果，可保存为 WAV。完全离线，不联网，不上传录音。

## 得到 APK 的两种方法

**方法 A：Android Studio（最直接）**
1. 用 Android Studio（Ladybug 以上）打开本目录，等 Gradle 同步完成。
2. 菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)。
3. APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

命令行等价：`./gradlew assembleDebug`

**方法 B：GitHub 云端自动打包（不用装任何东西）**
1. 在 GitHub 新建一个仓库，把本目录全部文件上传（含隐藏目录 `.github`）。
2. 打开仓库的 Actions 页，等 "Build APK" 跑完（约 3~5 分钟）。
3. 点进该次运行，在底部 Artifacts 下载 `VoiceChanger-debug-apk`，解压得到 APK。

安装：把 APK 发到手机，点开安装（需要允许"安装未知来源应用"）。要求 Android 8.0 及以上。

## 音色

| 音色 | 音高 | 语速 | 其他处理 |
|------|------|------|----------|
| 原声 | 0 | 1.00 | 无 |
| 少女音 | +5 半音 | 1.00 | 提亮高频，轻微气声 |
| 少妇音 | +3.5 半音 | 0.97 | 轻微气声，极轻的音高颤动 |
| 老妇音 | +2.5 半音 | 0.88 | 压暗高频、沙哑噪声、音高颤动、音量颤抖 |
| 儿童音 | +8.5 半音 | 1.05 | 提亮高频 |

想调音色，只改 `VoiceEffect.kt`。

## 已知限制

- 这是"效果器式"变声（变调 + 音色修饰），不是 AI 声音克隆。男声输入效果最好；输入本身是女声时，少女音、儿童音会偏尖。
- 录音最长 60 秒。
