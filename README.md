# BgVideoRecorder 后台视频录制

一个最小可用的安卓示例：主界面只有一个按钮，点击后按钮文字切换为「停止录制」并开始调用摄像头录制视频；
**不在屏幕上显示任何预览画面**；通过前台服务在应用进入后台后继续录制；再次点击按钮恢复文字并结束录制。
视频文件保存到应用私有目录，无需任何存储权限。

- 目标版本（targetSdk）：**31（Android 12）**
- minSdk：26（Android 8.0）
- 录制实现：Camera2 + MediaRecorder，无预览 Surface
- 输出目录：`getExternalFilesDir(null)/Movies/VID_yyyyMMdd_HHmmss.mp4`（应用沙盒，卸载即清）

## 目录结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/bgvideo/
│   ├── MainActivity.kt        // 单按钮界面 + 权限申请
│   └── RecordingService.kt    // 前台服务：Camera2 + MediaRecorder 录制
└── res/                        // 布局、字符串、图标、通知小图标
```

## 构建与运行

1. 用 **Android Studio** 打开 `BgVideoRecorder/` 根目录，会自动生成 gradle wrapper 并同步依赖。
   或在已安装 Gradle 的环境执行 `gradle wrapper` 生成 wrapper 后 `./gradlew assembleDebug`。
2. 连接真机（Android 8.0+，建议 Android 12）运行 `app`。

## 使用流程

1. 首次点击「开始录制」→ 系统弹出相机/录音（Android 13 还含通知）权限申请，授予后即开始录制。
2. 此时按钮变为「停止录制」，状态栏出现前台服务通知；按 Home 把应用切到后台，**录制继续**。
3. 再次点击按钮（或点通知回到 App 后点击）→ 结束录制，文件写入私有目录，按钮恢复为「开始录制」。

## 关键点说明

- **无预览**：Camera2 的 `createCaptureSession` 只注册 `MediaRecorder.surface` 一个输出，没有
  `TextureView`/`SurfaceView`，因此主界面与后台都没有画面。
- **后台持续录制**：`RecordingService` 为前台服务，`foregroundServiceType="camera|microphone"`，
  Android 10+ 用 `startForeground(id, notification, type)` 同时声明相机+麦克风类型，Android 14
  也已满足强制类型声明要求。
- **私有目录**：`getExternalFilesDir` 属应用沙盒，Android 10+ 无需 `WRITE_EXTERNAL_STORAGE`。
- **线程**：所有 Camera2/MediaRecorder 操作在专用 `HandlerThread` 上执行，避免阻塞主线程与回调竞争。

## 已知限制

- 视频固定 1280×720@30fps、4Mbps、H264+AAC；如需自适应分辨率可读取
  `StreamConfigurationMap.getOutputSizes(MediaRecorder::class.java)` 自行选择。
- 极少数厂商 ROM 的后置摄像头在无预览 Surface 时可能无法配置会话；如遇到可加一个隐藏的
  `SurfaceTexture` 作为额外输出兼容。
- 录制方向按竖屏 `setOrientationHint(90)` 写死；横屏需求请按传感器方向动态计算。
