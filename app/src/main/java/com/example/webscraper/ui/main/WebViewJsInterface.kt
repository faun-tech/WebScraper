package com.example.webscraper.ui.main

import android.webkit.JavascriptInterface

/**
 * WebView 안에 주입된 JavaScript에서 호출하는 브릿지.
 * 클릭한 지점이 링크가 아닐 때, 해당 부분의 텍스트가 [onTextExtracted]로 전달된다.
 * [onTextExtracted]의 두 번째 인자는 파일명 제안이다.
 * 'page-desc' 클래스 요소 안에 숫자(회차 등)가 있으면 "{숫자}화 {제목}" 형태로,
 * 제목은 'theme-novel-title' 클래스 요소의 텍스트를 사용한다. 둘 다 없으면 빈 문자열이다.
 *
 * 매크로(자동 저장) 진행 중에는 클릭 없이도 매 페이지마다 본문이 추출되어
 * [onAutoExtractResult]로 전달된다. 본문을 끝내 찾지 못하면 첫 번째 인자가 빈 문자열로 온다.
 *
 * 주의: 이 메서드들은 WebView의 내부 스레드(메인 스레드 아님)에서 호출될 수 있으므로,
 * 콜백 내부에서 UI를 직접 조작하지 않아야 한다.
 * (이 앱에서는 Channel.trySend / StateFlow.value를 사용하므로 스레드 안전하다.)
 */
class WebViewJsInterface(
    private val onTextExtracted: (text: String, suggestedFileName: String) -> Unit,
    private val onAutoExtractResult: (text: String, suggestedFileName: String) -> Unit
) {

    @JavascriptInterface
    fun onTextExtracted(text: String, suggestedFileName: String) {
        onTextExtracted.invoke(text, suggestedFileName)
    }

    @JavascriptInterface
    fun onAutoExtractResult(text: String, suggestedFileName: String) {
        onAutoExtractResult.invoke(text, suggestedFileName)
    }
}
