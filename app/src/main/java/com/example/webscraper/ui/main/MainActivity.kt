package com.example.webscraper.ui.main

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.webscraper.databinding.ActivityMainBinding
import com.example.webscraper.util.CLICK_TEXT_EXTRACTOR_SCRIPT
import com.example.webscraper.util.JS_BRIDGE_NAME
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupUrlBar()
        observeViewModel()
        handleBackPresses()
    }

    private fun setupWebView() {
        val webView = binding.webView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.addJavascriptInterface(
            WebViewJsInterface(onTextExtracted = viewModel::onTextExtracted),
            JS_BRIDGE_NAME
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(CLICK_TEXT_EXTRACTOR_SCRIPT, null)
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

    private fun navigateToTypedUrl() {
        viewModel.onUrlInputChanged(binding.editUrl.text.toString())
        viewModel.onNavigateClicked()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.LoadUrl -> {
                            binding.editUrl.setText(event.url)
                            binding.webView.loadUrl(event.url)
                        }

                        is UiEvent.RequestSaveFile -> {
                            createDocumentLauncher.launch(event.suggestedFileName)
                        }

                        is UiEvent.ShowToast -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
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
