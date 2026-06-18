package com.example.webscraper.ui.main

import android.net.Uri

/**
 * ViewModel -> View로 전달되는 1회성 이벤트.
 */
sealed class UiEvent {

    /** WebView에 [url]을 로드하라는 이벤트 */
    data class LoadUrl(val url: String) : UiEvent()

    /** 추출된 텍스트를 [suggestedFileName] 이름으로 저장할 위치를 사용자에게 선택받으라는 이벤트 */
    data class RequestSaveFile(val suggestedFileName: String) : UiEvent()

    /**
     * WebView 안에서의 탐색(링크 클릭, 리다이렉트 등)으로 실제 URL이 [url]로 바뀌었으니
     * 주소창 표시만 갱신하라는 이벤트. (LoadUrl과 달리 webView.loadUrl()을 다시 호출하지 않는다.)
     */
    data class UpdateAddressBar(val url: String) : UiEvent()

    /** 짧은 메시지를 사용자에게 보여주라는 이벤트 */
    data class ShowToast(val message: String) : UiEvent()

    /** 매크로 시작: 현재 URL에서 [startNumber]화를 감지했으니, 마지막 회차 숫자를 입력받으라는 이벤트 */
    data class RequestMacroEndNumber(val startNumber: Int) : UiEvent()

    /** 매크로로 저장할 폴더를 사용자에게 한 번 선택받으라는 이벤트 (OpenDocumentTree) */
    object RequestMacroFolder : UiEvent()

    /**
     * 매크로 시작: [baseUrl]의 회차 숫자를 [numbers] 순서대로 순회하며 [folderUri]에 저장하는
     * 작업을 포그라운드 서비스(MacroForegroundService)에서 시작하라는 이벤트.
     * 화면이 꺼지거나 앱이 백그라운드로 가도 계속 진행되도록 Activity 안이 아니라
     * 별도 서비스에서 돌린다.
     */
    data class StartMacroService(val baseUrl: String, val numbers: List<Int>, val folderUri: Uri) : UiEvent()

    /** 실행 중인 매크로 포그라운드 서비스를 취소하라는 이벤트. */
    object CancelMacroService : UiEvent()
}
