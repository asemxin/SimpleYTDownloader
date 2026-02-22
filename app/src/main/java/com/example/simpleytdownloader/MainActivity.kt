package com.example.simpleytdownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.simpleytdownloader.databinding.ActivityMainBinding
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isDownloading = false
    private var currentProcessId: String? = null
    private var selectedQuality = 720  // 默认 720p

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val MAX_RETRIES = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
        handleSharedIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleSharedIntent(it) }
    }

    private fun handleSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val url = extractUrl(sharedText)
                if (url != null) {
                    binding.urlInput.setText(url)
                    Toast.makeText(this, "链接已粘贴", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        return urlPattern.find(text)?.value
    }

    private fun setupUI() {
        // 粘贴按钮
        binding.pasteButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    val url = extractUrl(text)
                    if (url != null) {
                        binding.urlInput.setText(url)
                        Toast.makeText(this, "链接已粘贴", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "未找到有效链接", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 获取信息按钮（可选功能，不强制）
        binding.fetchInfoButton.setOnClickListener {
            val url = binding.urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                fetchVideoInfo(url)
            } else {
                Toast.makeText(this, "请输入视频链接", Toast.LENGTH_SHORT).show()
            }
        }

        // 下载视频按钮
        binding.downloadVideoButton.setOnClickListener {
            startDownload(audioOnly = false)
        }

        // 下载音频按钮
        binding.downloadAudioButton.setOnClickListener {
            startDownload(audioOnly = true)
        }

        // 取消按钮
        binding.cancelButton.setOnClickListener {
            cancelDownload()
        }

        // 更新 yt-dlp 按钮
        binding.updateButton.setOnClickListener {
            updateYtDlp()
        }

        // 清晰度选择
        binding.qualityChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedQuality = when (checkedIds[0]) {
                    R.id.quality360 -> 360
                    R.id.quality480 -> 480
                    R.id.quality720 -> 720
                    R.id.quality1080 -> 1080
                    R.id.qualityBest -> 0  // 0 表示最高清晰度
                    else -> 720
                }
            }
        }
    }

    // ===== 日志系统 =====

    private fun log(msg: String) {
        runOnUiThread {
            val current = binding.logText.text.toString()
            val newText = if (current.isNotEmpty()) "$current\n$msg" else msg
            binding.logText.text = newText
            // 自动滚动到底部
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun logSeparator() {
        log("────────────────────────────────────")
    }

    // ===== 权限检查 =====

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    // ===== 获取视频信息（可选） =====

    private fun fetchVideoInfo(url: String) {
        if (!App.isInitialized) {
            Toast.makeText(this, "正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        binding.statusText.text = "正在获取视频信息..."
        log("🔍 正在获取视频信息...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(url)

                withContext(Dispatchers.Main) {
                    displayVideoInfo(videoInfo)
                    setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "获取失败: ${e.message}"
                    log("❌ 获取失败: ${e.message}")
                    setLoading(false)
                }
            }
        }
    }

    private fun displayVideoInfo(info: VideoInfo) {
        val duration = info.duration?.let {
            val minutes = it / 60
            val seconds = it % 60
            "${minutes}分${seconds}秒"
        } ?: "未知"

        val infoStr = buildString {
            appendLine("📺 标题: ${info.title ?: "未知"}")
            appendLine("👤 作者: ${info.uploader ?: "未知"}")
            appendLine("⏱️ 时长: $duration")
            appendLine("📊 分辨率: ${info.width ?: "?"}x${info.height ?: "?"}")
        }

        binding.videoInfoText.text = infoStr
        binding.videoInfoCard.visibility = View.VISIBLE
        binding.statusText.text = "✅ 视频信息获取成功"

        // 同步写入日志
        log("📺 标题: ${info.title ?: "未知"}")
        log("👤 作者: ${info.uploader ?: "未知"}")
        log("⏱️ 时长: $duration")
    }

    // ===== 下载（含自动重试） =====

    private fun startDownload(audioOnly: Boolean) {
        val url = binding.urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入视频链接", Toast.LENGTH_SHORT).show()
            return
        }

        if (!App.isInitialized) {
            Toast.makeText(this, "正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        if (isDownloading) {
            Toast.makeText(this, "正在下载中...", Toast.LENGTH_SHORT).show()
            return
        }

        isDownloading = true
        currentProcessId = "download_${System.currentTimeMillis()}"
        setLoading(true)
        binding.cancelButton.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0

        // 启动前台服务，防止后台被系统杀死
        val serviceIntent = Intent(this, DownloadService::class.java)
        startService(serviceIntent)

        // 清空日志并重新开始
        binding.logText.text = ""
        log("📎 链接: $url")

        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SimpleYTDownloader"
        )
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        log("📁 目录: ${downloadDir.absolutePath}")
        logSeparator()

        val typeLabel = if (audioOnly) "音频" else "视频"

        lifecycleScope.launch(Dispatchers.IO) {
            // 下载前自动更新 yt-dlp
            withContext(Dispatchers.Main) {
                binding.statusText.text = "🔍 检查 yt-dlp 更新..."
                log("🔍 检查 yt-dlp 更新...")
            }
            try {
                val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(
                    this@MainActivity,
                    YoutubeDL.UpdateChannel.NIGHTLY
                )
                withContext(Dispatchers.Main) {
                    when (updateStatus) {
                        YoutubeDL.UpdateStatus.DONE -> log("✅ yt-dlp 已更新到最新版")
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> log("✅ yt-dlp 已是最新版")
                        else -> log("ℹ️ yt-dlp 更新状态: $updateStatus")
                    }
                    logSeparator()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("⚠️ yt-dlp 更新检查失败: ${e.message?.take(80)}，继续下载...")
                    logSeparator()
                }
            }

            var lastError: String? = null

            for (attempt in 1..MAX_RETRIES) {
                try {
                    if (attempt > 1) {
                        withContext(Dispatchers.Main) {
                            log("🔄 第 $attempt 次重试...")
                            binding.statusText.text = "🔄 第 $attempt 次重试..."
                        }
                    }

                    // 先获取视频信息
                    if (attempt == 1) {
                        withContext(Dispatchers.Main) {
                            binding.statusText.text = "获取${typeLabel}信息..."
                            binding.progressBar.progress = 0
                        }

                        try {
                            val videoInfo = YoutubeDL.getInstance().getInfo(url)
                            val duration = videoInfo.duration?.let {
                                val min = it / 60
                                val sec = it % 60
                                "${min}分${sec}秒"
                            } ?: "未知"

                            withContext(Dispatchers.Main) {
                                log("📺 标题: ${videoInfo.title ?: "未知"}")
                                log("⏱️ 时长: $duration")
                                // 同时更新信息卡片
                                displayVideoInfo(videoInfo)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                log("⚠️ 获取信息失败，继续下载...")
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        binding.statusText.text = "开始下载${typeLabel}..."
                        binding.progressBar.progress = 5
                    }

                    val request = YoutubeDLRequest(url).apply {
                        addOption("-o", "${downloadDir.absolutePath}/%(title).100s.%(ext)s")
                        addOption("--no-mtime")
                        addOption("--restrict-filenames")
                        addOption("--no-warnings")

                        // 网络稳定性配置（对齐 Windows 版）
                        addOption("--retries", "10")
                        addOption("--fragment-retries", "10")
                        addOption("--file-access-retries", "3")
                        addOption("--extractor-retries", "5")
                        addOption("--socket-timeout", "30")
                        addOption("--http-chunk-size", "10M")

                        // 绕过 YouTube 403 - 使用 mweb 客户端，不需要 PO Token
                        addOption("--extractor-args", "youtube:player_client=mweb,android,ios")

                        // User-Agent
                        addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")

                        if (audioOnly) {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                        } else {
                            // 对齐 Windows 版：去掉 ext 限制，提升兼容性
                            val formatString = if (selectedQuality == 0) {
                                "bestvideo+bestaudio/best"
                            } else {
                                "bestvideo[height<=${selectedQuality}]+bestaudio/best[height<=${selectedQuality}]/best"
                            }
                            addOption("-f", formatString)
                            addOption("--merge-output-format", "mp4")
                        }
                    }

                    YoutubeDL.getInstance().execute(
                        request,
                        currentProcessId
                    ) { progress, etaInSeconds, _ ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            val progressInt = progress.toInt().coerceIn(0, 100)

                            // 构建状态文本（对齐 Windows 版风格）
                            val statusParts = mutableListOf<String>()
                            statusParts.add("下载中 ${progressInt}%")

                            if (etaInSeconds > 0) {
                                statusParts.add("剩余 ${etaInSeconds}s")
                            }

                            val statusStr = statusParts.joinToString(" | ")

                            binding.progressBar.progress = progressInt
                            binding.statusText.text = "📥 $statusStr"
                        }
                    }

                    // 下载成功
                    withContext(Dispatchers.Main) {
                        logSeparator()
                        log("✅ ${typeLabel}下载成功！")
                        log("📁 保存至: ${downloadDir.absolutePath}")
                        binding.progressBar.progress = 100
                        binding.statusText.text = "✅ 下载完成！"
                        Toast.makeText(this@MainActivity, "${typeLabel}下载完成！", Toast.LENGTH_LONG).show()
                        resetDownloadState()
                    }
                    return@launch  // 成功，退出

                } catch (e: Exception) {
                    val errorMsg = e.message ?: "未知错误"
                    lastError = errorMsg

                    // 检查是否为取消操作
                    if (errorMsg.contains("cancel", ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            binding.statusText.text = "⏹️ 下载已取消"
                            log("⏹️ 下载已取消")
                            resetDownloadState()
                        }
                        return@launch
                    }

                    // 检查是否为网络/403错误
                    val isNetworkError = listOf(
                        "ssl", "eof", "connection", "timeout", "reset", "network",
                        "http", "socket", "broken pipe", "403", "forbidden"
                    ).any { errorMsg.lowercase().contains(it) }

                    if (isNetworkError && attempt < MAX_RETRIES) {
                        withContext(Dispatchers.Main) {
                            log("⚠️ 网络错误，3秒后重试: ${errorMsg.take(100)}...")
                            binding.statusText.text = "⚠️ 网络错误，准备重试..."
                        }
                        // 生成新的 processId 用于重试
                        currentProcessId = "download_${System.currentTimeMillis()}"
                        delay(3000)
                        continue
                    } else {
                        // 最后一次尝试或非网络错误
                        break
                    }
                }
            }

            // 所有重试都失败
            withContext(Dispatchers.Main) {
                logSeparator()
                log("❌ 下载失败 (重试${MAX_RETRIES}次): $lastError")
                binding.statusText.text = "❌ 下载失败"
                Toast.makeText(
                    this@MainActivity,
                    "下载失败: ${lastError?.take(200)}",
                    Toast.LENGTH_LONG
                ).show()
                resetDownloadState()
            }
        }
    }

    private fun cancelDownload() {
        currentProcessId?.let { processId ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (e: Exception) {
                    // 忽略取消错误
                }
            }
        }
        binding.statusText.text = "⏹️ 正在取消..."
        log("⏹️ 正在取消...")
    }

    private fun resetDownloadState() {
        isDownloading = false
        currentProcessId = null
        setLoading(false)
        binding.cancelButton.visibility = View.GONE
        binding.progressBar.visibility = View.GONE

        // 停止前台服务
        stopService(Intent(this, DownloadService::class.java))
    }

    private fun updateYtDlp() {
        if (!App.isInitialized) {
            Toast.makeText(this, "正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        binding.statusText.text = "正在更新 yt-dlp..."
        log("🔄 检查 yt-dlp 版本...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(
                    this@MainActivity,
                    YoutubeDL.UpdateChannel.NIGHTLY
                )

                withContext(Dispatchers.Main) {
                    when (updateStatus) {
                        YoutubeDL.UpdateStatus.DONE -> {
                            binding.statusText.text = "✅ yt-dlp 更新成功！"
                            log("✅ yt-dlp 更新成功！")
                            Toast.makeText(this@MainActivity, "更新成功！", Toast.LENGTH_SHORT).show()
                        }
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                            binding.statusText.text = "✅ yt-dlp 已是最新版本"
                            log("✅ yt-dlp 已是最新版本")
                            Toast.makeText(this@MainActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            binding.statusText.text = "更新状态: $updateStatus"
                            log("ℹ️ 更新状态: $updateStatus")
                        }
                    }
                    setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "❌ 更新失败: ${e.message}"
                    log("❌ 更新失败: ${e.message}")
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        binding.fetchInfoButton.isEnabled = !loading
        binding.downloadVideoButton.isEnabled = !loading && !isDownloading
        binding.downloadAudioButton.isEnabled = !loading && !isDownloading
        binding.updateButton.isEnabled = !loading
    }
}
