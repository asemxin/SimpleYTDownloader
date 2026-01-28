# SimpleYTDownloader

一个简单的 YouTube 视频下载器 Android 应用，基于 [youtubedl-android](https://github.com/yausername/youtubedl-android) 库。

## ✨ 功能

- 📹 下载视频（MP4 格式）
- 🎵 下载音频（MP3 格式）
- 🔗 从其他应用分享链接直接下载
- 📋 一键粘贴链接
- 🔄 支持更新 yt-dlp 到最新版本
- ⚡ 使用 aria2c 多线程加速下载

## 📱 支持的平台

基于 yt-dlp，支持 1000+ 网站，包括：
- YouTube
- Bilibili
- Twitter/X
- TikTok
- Instagram
- 更多...

## 📥 下载

### 方式一：GitHub Actions 自动构建

1. Fork 这个仓库到你的 GitHub
2. 进入你 Fork 的仓库，点击 **Actions** 标签
3. 点击 **Build APK** 工作流
4. 点击 **Run workflow** 按钮
5. 等待构建完成后，下载 Artifacts 中的 APK

### 方式二：创建 Release

1. 在你 Fork 的仓库中创建一个 tag（如 `v1.0.0`）
2. GitHub Actions 会自动构建并创建 Release
3. 从 Release 页面下载 APK

## 🏗️ 本地构建

### 环境要求

- JDK 17+
- Android SDK
- Gradle 8.2+

### 构建命令

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

APK 输出位置：`app/build/outputs/apk/`

## 📁 下载位置

下载的文件保存在：`Downloads/SimpleYTDownloader/`

## 🔧 技术栈

- **语言**: Kotlin
- **UI**: Android View + Material Components
- **核心库**: youtubedl-android (yt-dlp + Python + ffmpeg)
- **下载加速**: aria2c

## 📄 许可证

MIT License

## 🙏 致谢

- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [youtubedl-android](https://github.com/yausername/youtubedl-android)
- [Seal](https://github.com/JunkFood02/Seal) - 参考项目
