package com.example.webscraper.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webscraper.data.local.ViewerPreferences
import com.example.webscraper.data.model.ViewerSettings
import com.example.webscraper.data.repository.TextFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TextViewerViewModel @Inject constructor(
    private val textFileRepository: TextFileRepository,
    private val viewerPreferences: ViewerPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TextViewerUiState(settings = viewerPreferences.getSettings())
    )
    val uiState: StateFlow<TextViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<TextViewerUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // 화면 회전 등으로 ViewModel이 재사용될 때 폴더를 다시 읽지 않도록 한 번만 초기화한다.
    private var initialized = false

    // 현재 열려 있는 폴더. "마지막으로 본 파일" 기록을 폴더별로 남기는 데 쓴다.
    private var currentFolderUri: Uri? = null

    /**
     * 화면 진입 시 한 번 호출된다. [folderUri] 안의 텍스트 파일들을 읽어 목록을 구성한다.
     * 시작 파일은 [startFileUri]가 그 안에 있으면 그 파일, 없으면 이 폴더에서 마지막으로
     * 보던 파일(있다면), 그것도 없으면 첫 파일이다. 시작 파일을 열 때는 마지막으로 보던
     * 스크롤 위치도 함께 복원한다.
     */
    fun initFolder(folderUri: Uri, startFileUri: Uri?) {
        currentFolderUri = folderUri
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            textFileRepository.listTextFiles(folderUri)
                .onSuccess { files ->
                    if (files.isEmpty()) {
                        _uiState.value = _uiState.value.copy(isLoadingFolder = false)
                        _events.send(TextViewerUiEvent.ShowToast("폴더 안에 텍스트 파일이 없습니다."))
                        return@onSuccess
                    }
                    val resolvedStartUri = startFileUri ?: viewerPreferences.getLastFileUri(folderUri)
                    val startIndex = resolvedStartUri
                        ?.let { uri -> files.indexOfFirst { it.uri == uri } }
                        ?.takeIf { it >= 0 }
                        ?: 0
                    _uiState.value = _uiState.value.copy(
                        files = files,
                        currentIndex = startIndex,
                        isLoadingFolder = false
                    )
                    loadCurrentFile()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoadingFolder = false)
                    _events.send(TextViewerUiEvent.ShowToast("폴더를 여는 데 실패했습니다: ${error.message}"))
                }
        }
    }

    /** 다음 파일(이름순으로 한 칸 뒤)로 이동한다. 이동 전 [currentScrollY] 위치를 현재 파일 것으로 저장한다. */
    fun onNextClicked(currentScrollY: Int) {
        val state = _uiState.value
        if (!state.hasNext) return
        saveScrollPosition(currentScrollY)
        _uiState.value = state.copy(currentIndex = state.currentIndex + 1)
        loadCurrentFile()
    }

    /** 이전 파일(이름순으로 한 칸 앞)로 이동한다. 이동 전 [currentScrollY] 위치를 현재 파일 것으로 저장한다. */
    fun onPreviousClicked(currentScrollY: Int) {
        val state = _uiState.value
        if (!state.hasPrevious) return
        saveScrollPosition(currentScrollY)
        _uiState.value = state.copy(currentIndex = state.currentIndex - 1)
        loadCurrentFile()
    }

    /**
     * 목록 다이얼로그에서 [index]번째 파일을 선택했을 때 그 파일로 바로 이동한다.
     * 이동 전 [currentScrollY] 위치를 현재 파일 것으로 저장한다.
     */
    fun onFileSelected(index: Int, currentScrollY: Int) {
        val state = _uiState.value
        if (index !in state.files.indices || index == state.currentIndex) return
        saveScrollPosition(currentScrollY)
        _uiState.value = state.copy(currentIndex = index)
        loadCurrentFile()
    }

    /**
     * 현재 보고 있는 파일의 스크롤 위치([scrollY])를 저장한다. 화면을 벗어나기 전(다음/이전
     * 파일로 이동하기 직전, 액티비티가 멈출 때)에 호출한다.
     */
    fun saveScrollPosition(scrollY: Int) {
        val file = _uiState.value.currentFile ?: return
        viewerPreferences.saveScrollPosition(file.uri, scrollY)
        currentFolderUri?.let { viewerPreferences.saveLastFileUri(it, file.uri) }
    }

    /** 배경색/글자색/글자크기 설정이 바뀌었을 때 호출된다. 즉시 반영하고 기기에 저장한다. */
    fun onSettingsChanged(settings: ViewerSettings) {
        _uiState.value = _uiState.value.copy(settings = settings)
        viewerPreferences.saveSettings(settings)
    }

    private fun loadCurrentFile() {
        val file = _uiState.value.currentFile ?: return
        _uiState.value = _uiState.value.copy(isLoadingFile = true, currentText = "")

        viewModelScope.launch {
            textFileRepository.readText(file.uri)
                .onSuccess { text ->
                    _uiState.value = _uiState.value.copy(currentText = text, isLoadingFile = false)
                    // 이 폴더에서 마지막으로 연 파일을 즉시 기록해, 도중에 앱이 종료되어도
                    // 다음에 같은 폴더를 열면 적어도 이 파일부터 다시 보여줄 수 있게 한다.
                    currentFolderUri?.let { viewerPreferences.saveLastFileUri(it, file.uri) }
                    val savedScrollY = viewerPreferences.getScrollPosition(file.uri)
                    _events.send(TextViewerUiEvent.RestoreScroll(savedScrollY))
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoadingFile = false)
                    _events.send(TextViewerUiEvent.ShowToast("파일을 여는 데 실패했습니다: ${error.message}"))
                }
        }
    }
}
