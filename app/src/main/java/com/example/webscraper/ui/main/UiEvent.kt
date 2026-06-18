package com.example.webscraper.ui.main

/**
 * ViewModel -> View로 전달되는 1회성 이벤트.
 */
sealed class UiEvent {

    /** WebView에 [url]을 로드하라는 이벤트 */
    data class LoadUrl(val url: String) : UiEvent()

    /** 추출된 텍스트를 [suggestedFileName] 이름으로 저장할 위치를 사용자에게 선택받으라는 이벤트 */
    data class RequestSaveFile(val suggestedFileName: String) : UiEvent()

    /** 짧은 메시지를 사용자에게 보여주라는 이벤트 */
    data class ShowToast(val message: String) : UiEvent()
}
