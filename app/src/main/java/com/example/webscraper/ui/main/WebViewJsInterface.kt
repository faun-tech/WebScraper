package com.example.webscraper.ui.main

import android.webkit.JavascriptInterface

/**
 * WebView 안에 주입된 JavaScript에서 호출하는 브릿지.
 * 클릭한 지점이 링크가 아닐 때, 해당 부분의 텍스트가 [onTextExtracted]로 전달된다.
 *
 * 주의: 이 메서드는 WebView의 내부 스레드(메인 스레드 아님)에서 호출될 수 있으므로,
 * [onTextExtracted] 콜백 내부에서 UI를 직접 조작하지 않아야 한다.
 * (이 앱에서는 Channel.trySend를 사용하므로 스레드 안전하다.)
 */
class WebViewJsInterface(
    private val onTextExtracted: (String) -> Unit
) {

    @JavascriptInterface
    fun onTextExtracted(text: String) {
        onTextExtracted.invoke(text)
    }
}
