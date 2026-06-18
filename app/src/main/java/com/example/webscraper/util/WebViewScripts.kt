package com.example.webscraper.util

/**
 * 페이지 로딩이 끝난 뒤 WebView에 주입하는 클릭 감지 스크립트.
 *
 * 동작:
 * 1. 클릭된 요소(또는 그 조상)가 href를 가진 <a> 태그이면 아무 것도 하지 않는다.
 *    (해당 클릭은 WebView의 기본 링크 이동 동작에 맡긴다.)
 * 2. 링크가 아니면, 클릭된 요소부터 최대 6단계까지 조상으로 올라가며
 *    텍스트가 있는 가장 가까운 요소의 텍스트를 찾아 AndroidBridge로 전달한다.
 */
const val CLICK_TEXT_EXTRACTOR_SCRIPT = """
(function() {
    if (window.__androidClickListenerAttached) {
        return;
    }
    window.__androidClickListenerAttached = true;

    document.addEventListener('click', function(event) {
        var node = event.target;
        var isLink = false;

        while (node) {
            if (node.tagName === 'A' && node.hasAttribute('href')) {
                isLink = true;
                break;
            }
            node = node.parentElement;
        }

        if (isLink) {
            return;
        }

        var textNode = event.target;
        var text = '';
        for (var i = 0; i < 6 && textNode; i++) {
            text = (textNode.innerText || textNode.textContent || '').trim();
            if (text.length > 0) {
                break;
            }
            textNode = textNode.parentElement;
        }

        if (text.length > 0 && window.AndroidBridge && window.AndroidBridge.onTextExtracted) {
            window.AndroidBridge.onTextExtracted(text);
        }
    }, true);
})();
"""

/** WebView에 addJavascriptInterface로 등록할 때 사용하는 이름. JS 스크립트의 window.AndroidBridge와 일치해야 한다. */
const val JS_BRIDGE_NAME = "AndroidBridge"
