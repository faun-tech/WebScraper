package com.example.webscraper.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webscraper.data.repository.TextFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val textFileRepository: TextFileRepository
) : ViewModel() {

    private val _urlInput = MutableStateFlow("https://")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // WebView JS 브릿지로부터 전달받은, 아직 저장 위치가 선택되지 않은 텍스트
    private var pendingText: String? = null

    fun onUrlInputChanged(value: String) {
        _urlInput.value = value
    }

    /** 상단 "이동하기" 버튼(또는 키보드의 이동) 클릭 시 호출 */
    fun onNavigateClicked() {
        val raw = _urlInput.value.trim()
        if (raw.isEmpty()) {
            _events.trySend(UiEvent.ShowToast("URL을 입력해주세요."))
            return
        }
        val normalized = normalizeUrl(raw)
        _urlInput.value = normalized
        _events.trySend(UiEvent.LoadUrl(normalized))
    }

    private fun normalizeUrl(input: String): String {
        return if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
        ) {
            input
        } else {
            "https://$input"
        }
    }

    /**
     * WebView 안에서 링크가 아닌 부분이 클릭되어 텍스트가 추출되었을 때 호출된다.
     * (WebViewJsInterface -> Activity -> 이 메서드)
     */
    fun onTextExtracted(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        pendingText = trimmed
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        _events.trySend(UiEvent.RequestSaveFile("extracted_$timestamp.txt"))
    }

    /** 사용자가 시스템 저장 다이얼로그에서 저장 위치([uri])를 선택했을 때 호출된다. */
    fun onSaveLocationSelected(uri: Uri) {
        val text = pendingText
        pendingText = null
        if (text == null) return

        viewModelScope.launch {
            textFileRepository.saveText(uri, text)
                .onSuccess {
                    _events.send(UiEvent.ShowToast("텍스트를 파일로 저장했습니다."))
                }
                .onFailure { error ->
                    _events.send(UiEvent.ShowToast("저장 실패: ${error.message}"))
                }
        }
    }

    /** 사용자가 저장 다이얼로그를 취소한 경우 호출 */
    fun onSaveLocationCancelled() {
        pendingText = null
    }
}
