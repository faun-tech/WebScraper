package com.example.webscraper.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webscraper.data.local.UrlHistoryStore
import com.example.webscraper.data.repository.TextFileRepository
import com.example.webscraper.macro.MacroProgress
import com.example.webscraper.macro.MacroProgressHolder
import com.example.webscraper.util.UrlNumberSequence
import com.example.webscraper.util.buildTextFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val textFileRepository: TextFileRepository,
    private val macroProgressHolder: MacroProgressHolder,
    private val urlHistoryStore: UrlHistoryStore
) : ViewModel() {

    private val _urlInput = MutableStateFlow("https://")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // WebView JS 브릿지로부터 전달받은, 아직 저장 위치가 선택되지 않은 텍스트
    private var pendingText: String? = null

    // 매크로 진행 상태는 실제로 회차를 순회하는 MacroForegroundService가 갱신하고,
    // 여기서는 화면 표시를 위해 그대로 구독만 한다.
    val macroProgress: StateFlow<MacroProgress> = macroProgressHolder.progress

    // 매크로 버튼을 누른 시점부터, 종료 회차/폴더가 모두 정해질 때까지 임시로 들고 있는 값들.
    private var pendingMacroStartNumber: Int? = null
    private var pendingMacroBaseUrl: String? = null
    private var macroNumbers: List<Int> = emptyList()

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
        urlHistoryStore.addUrl(normalized)
        _events.trySend(UiEvent.LoadUrl(normalized))
    }

    // ---------------------------------------------------------------------
    // URL 히스토리
    // ---------------------------------------------------------------------

    /** 히스토리 버튼 클릭 시 호출된다. 저장된 URL들을 최신순으로 보여주라는 이벤트를 보낸다. */
    fun onHistoryButtonClicked() {
        val history = urlHistoryStore.getHistory()
        if (history.isEmpty()) {
            _events.trySend(UiEvent.ShowToast("히스토리가 비어 있습니다."))
            return
        }
        _events.trySend(UiEvent.ShowUrlHistory(history))
    }

    /** 히스토리 목록에서 [url]을 선택했을 때 호출된다. 그 페이지로 다시 이동한다. */
    fun onHistoryUrlSelected(url: String) {
        _urlInput.value = url
        urlHistoryStore.addUrl(url)
        _events.trySend(UiEvent.LoadUrl(url))
    }

    /** 히스토리 다이얼로그에서 "전체 삭제"를 눌렀을 때 호출된다. */
    fun onHistoryClearRequested() {
        urlHistoryStore.clearHistory()
        _events.trySend(UiEvent.ShowToast("히스토리를 모두 삭제했습니다."))
    }

    /**
     * WebView 안에서의 탐색(페이지 내 링크 클릭, 리다이렉트 등)으로 실제 표시 중인 URL이
     * 바뀌었을 때 호출된다. (Activity의 WebViewClient -> 이 메서드)
     * 주소창 표시만 갱신하며, _urlInput에는 반영하지 않는다(그러면 다음에 사용자가 다시
     * "이동하기"를 누를 때 정작 입력하려는 게 아니라 현재 페이지 URL이 그대로 다시 로드될 수 있음).
     */
    fun onWebViewUrlChanged(url: String) {
        _events.trySend(UiEvent.UpdateAddressBar(url))
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
     *
     * [suggestedFileName]은 "{회차 숫자}화 {제목}" 형태(둘 중 하나만 있으면 그것만)이며,
     * 비어있지 않으면 파일명으로 사용한다. 없으면 타임스탬프 기반 이름으로 대체한다.
     */
    fun onTextExtracted(text: String, suggestedFileName: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        pendingText = trimmed
        _events.trySend(UiEvent.RequestSaveFile(buildTextFileName(suggestedFileName)))
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

    // ---------------------------------------------------------------------
    // 매크로 (자동 회차 저장)
    // ---------------------------------------------------------------------
    // 실제 회차 순회/추출/저장은 MacroForegroundService가 담당한다(화면이 꺼지거나 앱이
    // 백그라운드로 가도 계속 진행되어야 하기 때문). 여기서는 시작 전 사용자 입력(마지막 회차,
    // 저장 폴더)을 모아 서비스 시작 이벤트를 보내는 역할만 한다.

    /**
     * 매크로 버튼 클릭 시 호출된다. [currentUrl](현재 WebView에 표시 중인 URL)의 끝에서
     * 회차 숫자를 찾고, 찾으면 사용자에게 마지막 회차 숫자를 입력받기 위한 이벤트를 보낸다.
     */
    fun onMacroButtonClicked(currentUrl: String) {
        if (macroProgressHolder.progress.value.isRunning) {
            _events.trySend(UiEvent.ShowToast("이미 매크로가 실행 중입니다."))
            return
        }

        val startNumber = UrlNumberSequence.extractTrailingNumber(currentUrl)
        if (startNumber == null) {
            _events.trySend(UiEvent.ShowToast("URL 끝부분에서 회차 숫자를 찾지 못했습니다."))
            return
        }

        pendingMacroStartNumber = startNumber
        pendingMacroBaseUrl = currentUrl
        _events.trySend(UiEvent.RequestMacroEndNumber(startNumber))
    }

    /** 사용자가 마지막 회차 숫자([endNumber])를 입력했을 때 호출된다. 다음으로 저장 폴더를 받는다. */
    fun onMacroEndNumberEntered(endNumber: Int) {
        val startNumber = pendingMacroStartNumber
        val baseUrl = pendingMacroBaseUrl
        if (startNumber == null || baseUrl == null) {
            return
        }

        macroNumbers = if (endNumber >= startNumber) {
            (startNumber..endNumber).toList()
        } else {
            (startNumber downTo endNumber).toList()
        }

        _events.trySend(UiEvent.RequestMacroFolder)
    }

    /** 사용자가 매크로 결과를 저장할 폴더([folderUri])를 선택했을 때 호출된다. 서비스 시작을 요청한다. */
    fun onMacroFolderSelected(folderUri: Uri) {
        val baseUrl = pendingMacroBaseUrl
        if (baseUrl == null || macroNumbers.isEmpty()) {
            resetPendingMacroState()
            return
        }

        _events.trySend(UiEvent.StartMacroService(baseUrl, macroNumbers, folderUri))
        resetPendingMacroState()
    }

    /** 사용자가 폴더 선택을 취소한 경우 호출된다. */
    fun onMacroFolderSelectionCancelled() {
        resetPendingMacroState()
        _events.trySend(UiEvent.ShowToast("매크로를 취소했습니다."))
    }

    /** 사용자가 매크로 중지를 요청했을 때 호출된다. */
    fun onMacroCancelRequested() {
        if (!macroProgressHolder.progress.value.isRunning) return
        _events.trySend(UiEvent.CancelMacroService)
    }

    private fun resetPendingMacroState() {
        pendingMacroStartNumber = null
        pendingMacroBaseUrl = null
        macroNumbers = emptyList()
    }
}
