package com.example.simpleytdownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isDownloading = false
    private var currentProcessId: String? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
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

        // 获取信息按钮
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
    }

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

    private fun fetchVideoInfo(url: String) {
        if (!App.isInitialized) {
            Toast.makeText(this, "正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        binding.statusText.text = "正在获取视频信息..."

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

        binding.videoInfoText.text = buildString {
            appendLine("📺 标题: ${info.title ?: "未知"}")
            appendLine("👤 作者: ${info.uploader ?: "未知"}")
            appendLine("⏱️ 时长: $duration")
            appendLine("📊 分辨率: ${info.width ?: "?"}x${info.height ?: "?"}")
        }
        binding.videoInfoCard.visibility = View.VISIBLE
        binding.downloadButtons.visibility = View.VISIBLE
        binding.statusText.text = "✅ 视频信息获取成功"
    }

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

        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SimpleYTDownloader"
        )
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
                    addOption("--no-mtime")
                    
                    if (audioOnly) {
                        addOption("-x")  // 仅提取音频
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")  // 最高质量
                    } else {
                        addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                        addOption("--merge-output-format", "mp4")
                    }

                    // 使用 aria2c 加速下载
                    addOption("--downloader", "libaria2c.so")
                    addOption("--downloader-args", "aria2c:'-x 16 -s 16 -k 1M'")
                }

                YoutubeDL.getInstance().execute(
                    request,
                    currentProcessId
                ) { progress, etaInSeconds, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val eta = if (etaInSeconds > 0) {
                            val min = etaInSeconds / 60
                            val sec = etaInSeconds % 60
                            "剩余 ${min}分${sec}秒"
                        } else ""
                        
                        binding.progressBar.progress = progress.toInt()
                        binding.statusText.text = "📥 下载中: ${progress.toInt()}% $eta"
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.statusText.text = "✅ 下载完成！保存至: ${downloadDir.absolutePath}"
                    Toast.makeText(this@MainActivity, "下载完成！", Toast.LENGTH_LONG).show()
                    resetDownloadState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e.message?.contains("cancel", ignoreCase = true) == true) {
                        binding.statusText.text = "⏹️ 下载已取消"
                    } else {
                        binding.statusText.text = "❌ 下载失败: ${e.message}"
                    }
                    resetDownloadState()
                }
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
    }

    private fun resetDownloadState() {
        isDownloading = false
        currentProcessId = null
        setLoading(false)
        binding.cancelButton.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun updateYtDlp() {
        if (!App.isInitialized) {
            Toast.makeText(this, "正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        binding.statusText.text = "正在更新 yt-dlp..."

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
                            Toast.makeText(this@MainActivity, "更新成功！", Toast.LENGTH_SHORT).show()
                        }
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                            binding.statusText.text = "✅ yt-dlp 已是最新版本"
                            Toast.makeText(this@MainActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            binding.statusText.text = "更新状态: $updateStatus"
                        }
                    }
                    setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "❌ 更新失败: ${e.message}"
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
