# 词组通（后台视频录制）

伪装成「词组背诵」app 的后台视频录制示例。

## 界面与用法

- **主界面**：词组列表（英文 + 中文释义，中文默认隐藏）。
  - 右下角 **+**：自定义添加词组（英文 + 中文）。
  - 底部 **「显示中文释义」** 按钮：点击 = 显示中文释义 **同时开始后台录制**，按钮文字变为「隐藏中文释义」；再次点击 = 隐藏中文释义 **同时结束录制**。（即原录制按钮的 start/stop 语义保留，仅文字伪装成语义开关。）
- **三击顶部标题「词组通」**：进入隐藏的「缓存管理」页面，列出已录制视频（大小 + 时间），单击播放、长按删除。
- 录制通过前台服务在进入后台后持续运行；视频保存到应用私有目录 `Android/data/com.example.bgvideo/files/Movies/VID_yyyyMMdd_HHmmss.mp4`（无需存储权限）。

## 录制规格

- Camera2 + MediaRecorder，无任何预览画面。
- 分辨率优先 **3840×2160**，非 4K 摄像头自动回退 1920×1080 / 1280×720。
- 码率随分辨率：16 / 8 / 4 Mbps；30fps，H264+AAC，竖屏方向按传感器校正。

## 工程

- targetSdk 31（Android 12）/ compileSdk 33 / minSdk 26
- 前台服务类型 `camera|microphone`
- 应用名「词组通」，图标沿用通用 adaptive-icon

## GitHub Actions 构建

`.github/workflows/build.yml` 在 push 到 main 时用 Gradle 7.5 + AGP 7.4.2 构建 debug APK，产物上传为 artifact `app-debug-apk`。