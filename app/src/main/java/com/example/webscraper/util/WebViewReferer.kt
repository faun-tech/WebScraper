package com.example.webscraper.util

import android.net.Uri
import android.webkit.WebView

/**
 * 일부 사이트는 Referer 헤더가 없는(=주소창에 직접 입력하거나 외부에서 들어온) 요청을
 * "정상적인 뷰어를 거치지 않은 접근"으로 보고 인증을 다시 요구한다. 실제 브라우저처럼
 * 직전에 열려있던 페이지의 URL([lastLoadedUrl])을 Referer로 함께 보내고, 첫 진입처럼
 * 이전 페이지가 없으면 최소한 같은 origin을 Referer로 보낸다.
 *
 * MainActivity(화면에 보이는 WebView)와 MacroForegroundService(headless WebView) 양쪽에서
 * 동일하게 사용한다.
 */
fun WebView.loadUrlWithReferer(url: String, lastLoadedUrl: String?) {
    val referer = lastLoadedUrl?.takeIf { it.isNotBlank() } ?: originOf(url)
    if (referer != null) {
        loadUrl(url, mapOf("Referer" to referer))
    } else {
        loadUrl(url)
    }
}

/** [url]의 "scheme://host/" 형태 origin을 돌려준다. 파싱할 수 없으면 null. */
fun originOf(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}/" else null
    } catch (e: Exception) {
        null
    }
}
