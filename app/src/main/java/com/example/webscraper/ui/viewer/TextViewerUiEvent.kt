package com.example.webscraper.ui.viewer

/**
 * TextViewerViewModel -> TextViewerActivity로 전달되는 1회성 이벤트.
 */
sealed class TextViewerUiEvent {

    /** 짧은 메시지를 사용자에게 보여주라는 이벤트 */
    data class ShowToast(val message: String) : TextViewerUiEvent()

    /** 방금 표시한 파일의 본문을 [scrollY] 위치(px)까지 스크롤해두라는 이벤트 */
    data class RestoreScroll(val scrollY: Int) : TextViewerUiEvent()
}
