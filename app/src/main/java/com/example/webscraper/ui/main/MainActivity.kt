package com.example.webscraper.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.webscraper.R
import com.example.webscraper.databinding.ActivityMainBinding
import com.example.webscraper.service.MacroForegroundService
import com.example.webscraper.ui.viewer.TextViewerActivity
import com.example.webscraper.util.CLICK_TEXT_EXTRACTOR_SCRIPT
import com.example.webscraper.util.CORE_SCRIPT
import com.example.webscraper.util.JS_BRIDGE_NAME
import com.example.webscraper.util.loadUrlWithReferer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    // Referer 헤더 계산용으로, 가장 최근에 로드가 "시작된" URL을 기억해둔다.
    // (다음 요청의 Referer = 바로 이전 페이지 URL, 즉 실제 브라우저의 동작과 비슷하게 만든다.)
    private var lastLoadedUrl: String? = null

    // 사용자가 시스템 "다른 이름으로 저장" 다이얼로그에서 저장 위치를 고르게 한다.
    // (별도 저장소 권한이 필요 없는 Storage Access Framework 방식)
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                viewModel.onSaveLocationSelected(uri)
            } else {
                viewModel.onSaveLocationCancelled()
            }
        }

    // 매크로 저장용 폴더를 한 번 선택받는다. 선택한 폴더에는 이후 계속 쓸 수 있도록
    // persistable 권한을 받아둔다.
    private val openDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onMacroFolderSelected(uri)
            } else {
                viewModel.onMacroFolderSelectionCancelled()
            }
        }

    // Android 13(API 33)부터는 알림을 보여주려면 런타임 권한이 필요하다. 거부해도 매크로
    // 자체는 동작하며(서비스는 알림 없이도 startForeground를 호출할 수 있다), 다만 진행
    // 상태 알림이 보이지 않을 수 있다.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과 무시 */ }

    // 뷰어로 열어볼 폴더를 한 번 선택받는다. 읽기 전용으로만 쓰므로 읽기 권한만 유지한다.
    private val openViewerFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(TextViewerActivity.createIntent(this, uri))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupUrlBar()
        setupMacroControls()
        setupViewerControls()
        setupHistoryControls()
        observeViewModel()
        handleBackPresses()
    }

    private fun setupWebView() {
        val webView = binding.webView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // 일부 사이트는 회차 본문이 별도 서브도메인/CDN에서 내려오면서 그쪽에 인증/조회 상태를
        // 쿠키로 저장한다. third-party 쿠키를 막아두면 그 상태가 유지되지 않아 매번 인증을
        // 요구할 수 있으므로 명시적으로 허용한다.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(
            WebViewJsInterface(
                onTextExtracted = viewModel::onTextExtracted,
                onAutoExtractResult = { _, _ -> /* 매크로 자동 추출은 MacroForegroundService에서 처리 */ }
            ),
            JS_BRIDGE_NAME
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 페이지 내 링크 클릭 등으로 URL이 바뀌면 주소창 표시도 함께 갱신한다.
                if (url != null) {
                    viewModel.onWebViewUrlChanged(url)
                    lastLoadedUrl = url
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(CORE_SCRIPT, null)
                view.evaluateJavascript(CLICK_TEXT_EXTRACTOR_SCRIPT, null)
                // 인증/조회 상태를 나타내는 쿠키가 디스크에 바로 반영되도록 한다.
                CookieManager.getInstance().flush()
            }
        }
    }

    private fun setupUrlBar() {
        binding.editUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateToTypedUrl()
                true
            } else {
                false
            }
        }

        binding.buttonGo.setOnClickListener {
            navigateToTypedUrl()
        }
    }

    private fun setupMacroControls() {
        binding.buttonMacro.setOnClickListener {
            viewModel.onMacroButtonClicked(binding.webView.url ?: "")
        }
        binding.buttonMacroCancel.setOnClickListener {
            viewModel.onMacroCancelRequested()
        }
    }

    /** 뷰어로 열어볼 폴더를 선택하게 한 뒤 TextViewerActivity를 띄운다. */
    private fun setupViewerControls() {
        binding.buttonViewer.setOnClickListener {
            openViewerFolderLauncher.launch(null)
        }
    }

    private fun setupHistoryControls() {
        binding.buttonHistory.setOnClickListener {
            viewModel.onHistoryButtonClicked()
        }
    }

    private fun navigateToTypedUrl() {
        viewModel.onUrlInputChanged(binding.editUrl.text.toString())
        viewModel.onNavigateClicked()
        hideKeyboard()
    }

    /** 주소창에 떠 있는 소프트 키보드를 닫고 포커스도 함께 해제한다. */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.editUrl.windowToken, 0)
        binding.editUrl.clearFocus()
    }

    private fun loadUrlWithReferer(url: String) {
        binding.webView.loadUrlWithReferer(url, lastLoadedUrl)
    }

    /** 매크로 시작 회차([startNumber])를 보여주고, 마지막 회차 숫자를 입력받는 다이얼로그. */
    private fun showMacroEndNumberDialog(startNumber: Int) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(startNumber.toString())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("매크로")
            .setMessage("$startNumber 화부터 시작합니다. 마지막 회차 숫자를 입력하세요.")
            .setView(input)
            .setPositiveButton("시작") { _, _ ->
                val endNumber = input.text.toString().toIntOrNull()
                if (endNumber == null) {
                    Toast.makeText(this, "올바른 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.onMacroEndNumberEntered(endNumber)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 최근 이동한 [urls](최신순) 중 하나를 골라 다시 그 페이지로 이동할 수 있는 목록 다이얼로그. */
    private fun showUrlHistoryDialog(urls: List<String>) {
        val items = urls.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_dialog_title)
            .setItems(items) { _, which ->
                viewModel.onHistoryUrlSelected(items[which])
            }
            .setNegativeButton(R.string.history_close_button, null)
            .setNeutralButton(R.string.history_clear_button) { _, _ ->
                viewModel.onHistoryClearRequested()
            }
            .show()
    }

    /** Android 13+에서 매크로 진행 알림을 보여주기 위해 필요하면 알림 권한을 요청한다. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.LoadUrl -> {
                            binding.editUrl.setText(event.url)
                            loadUrlWithReferer(event.url)
                        }

                        is UiEvent.UpdateAddressBar -> {
                            // 사용자가 주소창을 직접 편집 중이면 덮어쓰지 않는다.
                            if (!binding.editUrl.hasFocus()) {
                                binding.editUrl.setText(event.url)
                            }
                        }

                        is UiEvent.RequestSaveFile -> {
                            createDocumentLauncher.launch(event.suggestedFileName)
                        }

                        is UiEvent.ShowToast -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }

                        is UiEvent.RequestMacroEndNumber -> {
                            showMacroEndNumberDialog(event.startNumber)
                        }

                        is UiEvent.RequestMacroFolder -> {
                            openDocumentTreeLauncher.launch(null)
                        }

                        is UiEvent.StartMacroService -> {
                            requestNotificationPermissionIfNeeded()
                            val intent = MacroForegroundService.startIntent(
                                this@MainActivity,
                                event.baseUrl,
                                event.numbers,
                                event.folderUri
                            )
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        }

                        is UiEvent.CancelMacroService -> {
                            // 이 시점에는 서비스가 이미 startForeground()를 호출해 둔 상태이므로
                            // (취소 버튼은 매크로가 실행 중일 때만 보임) startForegroundService가
                            // 아닌 일반 startService로 취소 명령만 전달하면 된다.
                            startService(MacroForegroundService.cancelIntent(this@MainActivity))
                        }

                        is UiEvent.ShowUrlHistory -> {
                            showUrlHistoryDialog(event.urls)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.macroProgress.collect { progress ->
                    binding.macroStatusBar.visibility = if (progress.isRunning) View.VISIBLE else View.GONE
                    binding.textMacroStatus.text =
                        "매크로 진행 ${progress.current}/${progress.total}  저장 ${progress.savedCount}  실패 ${progress.failedCount}"
                }
            }
        }
    }

    private fun handleBackPresses() {
        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }
}
