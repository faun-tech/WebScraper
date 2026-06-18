package com.example.webscraper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.webscraper.R
import com.example.webscraper.data.repository.TextFileRepository
import com.example.webscraper.macro.MacroProgress
import com.example.webscraper.macro.MacroProgressHolder
import com.example.webscraper.ui.main.WebViewJsInterface
import com.example.webscraper.util.AUTO_EXTRACT_SCRIPT
import com.example.webscraper.util.CORE_SCRIPT
import com.example.webscraper.util.JS_BRIDGE_NAME
import com.example.webscraper.util.UrlNumberSequence
import com.example.webscraper.util.buildTextFileName
import com.example.webscraper.util.loadUrlWithReferer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 매크로(자동 회차 저장)를 화면/Activity 생명주기와 무관하게 계속 진행시키기 위한 포그라운드 서비스.
 *
 * Activity는 STARTED 상태를 벗어나면(화면 꺼짐, 백그라운드 전환) 이벤트 수집을 멈추기 때문에,
 * 매크로를 ViewModel/Activity 안에서 직접 돌리면 화면이 꺼지는 순간 진행이 멈춘다. 이 서비스는
 * 화면에 붙어있지 않은 headless WebView를 직접 들고 있다가, 포그라운드 서비스 알림을 띄운 채로
 * 화면 상태와 무관하게 회차를 순회하며 본문을 추출/저장한다. 진행 상태는 [MacroProgressHolder]를
 * 통해 Activity/ViewModel과 공유한다.
 */
@AndroidEntryPoint
class MacroForegroundService : Service() {

    @Inject
    lateinit var textFileRepository: TextFileRepository

    @Inject
    lateinit var macroProgressHolder: MacroProgressHolder

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + job)

    private var webView: WebView? = null

    private var macroBaseUrl: String = ""
    private var macroNumbers: List<Int> = emptyList()
    private var macroIndex: Int = 0
    private var macroFolderUri: Uri? = null

    // Referer 헤더 계산용으로, 가장 최근에 로드가 "시작된" URL을 기억해둔다.
    private var lastLoadedUrl: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)
                val numbers = intent.getIntArrayExtra(EXTRA_NUMBERS)?.toList()
                val folderUriString = intent.getStringExtra(EXTRA_FOLDER_URI)
                if (baseUrl != null && !numbers.isNullOrEmpty() && folderUriString != null) {
                    startMacro(baseUrl, numbers, Uri.parse(folderUriString))
                } else {
                    stopSelf()
                }
            }

            ACTION_CANCEL -> {
                stopMacro(showCancelledToast = true)
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        job.cancel()
        super.onDestroy()
    }

    private fun startMacro(baseUrl: String, numbers: List<Int>, folderUri: Uri) {
        macroBaseUrl = baseUrl
        macroNumbers = numbers
        macroIndex = 0
        macroFolderUri = folderUri
        lastLoadedUrl = null

        macroProgressHolder.update(
            MacroProgress(isRunning = true, current = 0, total = numbers.size, savedCount = 0, failedCount = 0)
        )

        startForeground(NOTIFICATION_ID, buildNotification(0, numbers.size))
        setupHeadlessWebView()
        startNextMacroStep()
    }

    private fun setupHeadlessWebView() {
        val wv = WebView(applicationContext)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)

        // 화면에 붙어있지 않은 WebView는 기본 크기가 0이라, "화면에 보일 때만" 콘텐츠를
        // 채우는 지연 로딩(예: IntersectionObserver 기반)이 동작하지 않을 수 있다.
        // 실제 화면에 떠 있는 것처럼 보이도록 임의의 화면 크기를 강제로 부여한다.
        // (사이트가 이런 방식의 지연 로딩을 쓰는 경우 이 처리로도 완전히 보장되지는 않는다.)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        wv.measure(widthSpec, heightSpec)
        wv.layout(0, 0, 1080, 1920)

        wv.addJavascriptInterface(
            WebViewJsInterface(
                onTextExtracted = { _, _ -> /* 헤드리스 서비스에서는 클릭이 없어 사용되지 않음 */ },
                onAutoExtractResult = ::onAutoExtractResult
            ),
            JS_BRIDGE_NAME
        )

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null) {
                    lastLoadedUrl = url
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(CORE_SCRIPT, null)
                view.evaluateJavascript(AUTO_EXTRACT_SCRIPT, null)
                CookieManager.getInstance().flush()
            }
        }

        webView = wv
    }

    // WebViewJsInterface 콜백 — WebView 내부 스레드에서 호출될 수 있으므로, 실제 처리는
    // serviceScope(Dispatchers.Main.immediate)로 넘긴다.
    private fun onAutoExtractResult(text: String, suggestedFileName: String) {
        serviceScope.launch {
            handleAutoExtractResult(text, suggestedFileName)
        }
    }

    private suspend fun handleAutoExtractResult(text: String, suggestedFileName: String) {
        if (!macroProgressHolder.progress.value.isRunning) return

        val folderUri = macroFolderUri
        val trimmed = text.trim()

        if (trimmed.isEmpty() || folderUri == null) {
            val progress = macroProgressHolder.progress.value
            macroProgressHolder.update(progress.copy(failedCount = progress.failedCount + 1))
            advanceMacro()
            return
        }

        val currentNumber = macroNumbers.getOrNull(macroIndex)
        val hint = suggestedFileName.ifBlank { currentNumber?.let { "${it}화" } ?: "" }
        val fileName = buildTextFileName(hint)

        val result = textFileRepository.saveTextToFolder(folderUri, fileName, trimmed)
        val progress = macroProgressHolder.progress.value
        macroProgressHolder.update(
            if (result.isSuccess) {
                progress.copy(savedCount = progress.savedCount + 1)
            } else {
                progress.copy(failedCount = progress.failedCount + 1)
            }
        )
        advanceMacro()
    }

    /** 현재 macroIndex가 가리키는 회차의 URL을 만들어 headless WebView에 로드한다. */
    private fun startNextMacroStep() {
        val number = macroNumbers.getOrNull(macroIndex)
        val url = number?.let { UrlNumberSequence.withTrailingNumber(macroBaseUrl, it) }
        if (number == null || url == null) {
            finishMacro()
            return
        }

        val progress = macroProgressHolder.progress.value
        macroProgressHolder.update(progress.copy(current = macroIndex + 1))
        updateNotification(macroIndex + 1, macroNumbers.size)
        webView?.loadUrlWithReferer(url, lastLoadedUrl)
    }

    /**
     * 현재 회차 처리가 끝났으니 다음 회차로 넘어가거나, 더 없으면 매크로를 종료한다.
     * 다음 회차 로드 전에 [MACRO_STEP_DELAY_MIN_MS]~[MACRO_STEP_DELAY_MAX_MS] 사이의
     * 무작위 시간만큼 대기해서, 너무 빠르고 일정한 연속 요청으로 차단되는 것을 피한다.
     */
    private fun advanceMacro() {
        macroIndex++
        if (macroIndex >= macroNumbers.size) {
            finishMacro()
        } else {
            serviceScope.launch {
                delay(Random.nextLong(MACRO_STEP_DELAY_MIN_MS, MACRO_STEP_DELAY_MAX_MS + 1))
                // 대기하는 동안 사용자가 매크로를 취소했을 수 있으므로, 그 사이 상태가
                // 바뀌지 않았는지 다시 확인한다.
                if (macroProgressHolder.progress.value.isRunning) {
                    startNextMacroStep()
                }
            }
        }
    }

    private fun finishMacro() {
        val progress = macroProgressHolder.progress.value
        showToast("매크로 완료: 저장 ${progress.savedCount}건, 실패 ${progress.failedCount}건")
        stopMacro(showCancelledToast = false)
    }

    private fun stopMacro(showCancelledToast: Boolean) {
        if (showCancelledToast) {
            showToast("매크로를 중지했습니다.")
        }
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        macroProgressHolder.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "매크로 진행 알림",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(current: Int, total: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("매크로 실행 중")
            .setContentText("$current / $total 회차 처리 중")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(current: Int, total: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(current, total))
    }

    companion object {
        private const val ACTION_START = "com.example.webscraper.action.START_MACRO"
        private const val ACTION_CANCEL = "com.example.webscraper.action.CANCEL_MACRO"
        private const val EXTRA_BASE_URL = "extra_base_url"
        private const val EXTRA_NUMBERS = "extra_numbers"
        private const val EXTRA_FOLDER_URI = "extra_folder_uri"

        private const val CHANNEL_ID = "macro_progress_channel"
        private const val NOTIFICATION_ID = 1001

        // 회차 사이를 너무 빠르고 일정하게 연속 요청하면 일부 사이트에서 봇/어뷰징으로 보고
        // IP 단위로 접근을 막을 수 있다. 사람이 읽는 듯한 변동폭을 주기 위해 이 범위 안에서
        // 무작위로 지연한다.
        private const val MACRO_STEP_DELAY_MIN_MS = 3000L
        private const val MACRO_STEP_DELAY_MAX_MS = 8000L

        fun startIntent(context: Context, baseUrl: String, numbers: List<Int>, folderUri: Uri): Intent {
            return Intent(context, MacroForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BASE_URL, baseUrl)
                putExtra(EXTRA_NUMBERS, numbers.toIntArray())
                putExtra(EXTRA_FOLDER_URI, folderUri.toString())
            }
        }

        fun cancelIntent(context: Context): Intent {
            return Intent(context, MacroForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
        }
    }
}
