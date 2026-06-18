package com.example.webscraper.util

/**
 * 클릭 추출 스크립트와 자동(매크로) 추출 스크립트가 함께 쓰는 핵심 함수들을
 * window.__webScraperCore에 등록한다. (이미 등록되어 있으면 아무 것도 하지 않음)
 *
 * - collectVisibleText(el): el 내부의 "보이는" 텍스트를 모은다. <p>/<div>/<li>/<br>에는
 *   줄바꿈을 넣고, <slot>은 실제로 투영된 라이트 DOM을, 오픈 섀도우 루트(el.shadowRoot)가
 *   있으면 그 안의 콘텐츠를 읽는다(섀도우 루트가 아직 비어있다면 빈 문자열이 반환되므로,
 *   호출하는 쪽에서 polling으로 재시도하면 자연스럽게 "open까지 기다리는" 효과가 된다).
 * - buildFileNameHint(): 'theme-novel-title' 클래스의 텍스트를 제목으로, 'page-desc'
 *   클래스 안의 숫자를 회차로 보고 "{숫자}화 {제목}" 형태의 파일명 제안을 만든다.
 */
const val CORE_SCRIPT = """
(function() {
    if (window.__webScraperCore) {
        return;
    }

    // "본문 불러오는 중..." 같은 로딩 placeholder에 흔히 쓰이는 클래스.
    // 이 클래스가 붙은 요소는 화면에 보이더라도(opacity 등으로 숨겨졌든 아니든)
    // 실제 본문이 아니므로 추출 대상에서 완전히 제외한다.
    var IGNORE_CLASS = 'wr-none';

    function isHidden(el) {
        if (el.classList && el.classList.contains(IGNORE_CLASS)) {
            return true;
        }
        var tag = el.tagName;
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT') {
            return true;
        }
        var style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden') {
            return true;
        }
        if (parseFloat(style.opacity) === 0) {
            return true;
        }
        return false;
    }

    // <p>, <div>, <li> 같은 블록 레벨 태그의 끝, 그리고 <br> 위치에는 줄바꿈을 넣어서
    // 문단 구분이 살아있는 텍스트로 저장한다.
    var BLOCK_TAGS = { 'P': true, 'DIV': true, 'LI': true };

    // 노드 목록(Element의 childNodes 또는 <slot>의 assignedNodes)을 순회하며 텍스트를 모은다.
    function collectFromNodeList(nodes) {
        var result = '';
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            if (node.nodeType === 3) {
                result += node.nodeValue;
            } else if (node.nodeType === 1) {
                if (node.tagName === 'BR') {
                    result += '\n';
                } else {
                    var childText = collectVisibleText(node);
                    result += childText;
                    if (BLOCK_TAGS[node.tagName] && childText.length > 0 &&
                        childText.charAt(childText.length - 1) !== '\n') {
                        result += '\n';
                    }
                }
            }
        }
        return result;
    }

    // innerText/textContent는 opacity:0 요소나 placeholder 요소의 텍스트까지 끌어올 수 있고,
    // 또한 오픈 섀도우 DOM(#shadow-root) 내부의 실제 렌더링 콘텐츠는 따로 들어가봐야 하므로
    // 직접 트리를 순회하며 텍스트를 모은다.
    function collectVisibleText(el) {
        if (isHidden(el)) {
            return '';
        }

        // <slot>은 자기 자신의 childNodes(fallback 콘텐츠) 대신, 실제로 투영된(assigned)
        // 라이트 DOM 노드를 읽어야 화면에 보이는 내용과 일치한다.
        if (el.tagName === 'SLOT' && typeof el.assignedNodes === 'function') {
            return collectFromNodeList(el.assignedNodes({ flatten: true }));
        }

        // 오픈 섀도우 루트가 있으면, 실제로 화면에 렌더링되는 내용은 섀도우 트리 쪽이다.
        // (라이트 DOM 자식은 섀도우 트리 안의 <slot>을 통해 투영될 때만 보이고,
        //  그 처리는 위 <slot> 분기에서 이루어진다.) 닫힌(closed) 섀도우 루트는
        //  외부 스크립트에서 접근 자체가 막혀 있어 이 방식으로는 읽을 수 없다.
        if (el.shadowRoot) {
            return collectFromNodeList(el.shadowRoot.childNodes);
        }

        return collectFromNodeList(el.childNodes);
    }

    function buildFileNameHint() {
        var titleEl = document.querySelector('.theme-novel-title');
        var title = titleEl ? collectVisibleText(titleEl).trim() : '';

        var descEl = document.querySelector('.page-desc');
        var pageDesc = descEl ? collectVisibleText(descEl).trim() : '';
        var numberMatch = pageDesc.match(/\d+/);
        var number = numberMatch ? numberMatch[0] : '';

        var fileNameHint;
        if (number && title) {
            fileNameHint = number + '화 ' + title;
        } else if (number) {
            fileNameHint = number + '화';
        } else {
            fileNameHint = title;
        }
        if (!fileNameHint) {
            // 제목/숫자를 둘 다 못 찾았으면 page-desc 텍스트라도 제안으로 사용한다.
            fileNameHint = pageDesc;
        }
        return fileNameHint;
    }

    window.__webScraperCore = {
        collectVisibleText: collectVisibleText,
        buildFileNameHint: buildFileNameHint
    };
})();
"""

/**
 * 페이지 로딩이 끝난 뒤 WebView에 주입하는 클릭 감지 스크립트. CORE_SCRIPT가 먼저
 * 주입되어 있어야 한다(window.__webScraperCore).
 *
 * 동작:
 * 1. 클릭된 요소(또는 그 조상)가 href를 가진 <a> 태그이거나, 버튼/입력창 등 조작용
 *    요소(BUTTON, INPUT, SELECT, TEXTAREA, LABEL, role="button", contenteditable)이거나,
 *    클래스 이름에 'dropdown'이 포함된 요소이면 아무 것도 하지 않는다.
 *    (해당 클릭은 페이지의 원래 동작에 맡긴다.)
 * 2. 그 외의 경우, 클릭된 요소부터 최대 6단계까지 조상으로 올라가며
 *    텍스트가 있는 가장 가까운 요소의 텍스트를 찾아 AndroidBridge로 전달한다.
 */
const val CLICK_TEXT_EXTRACTOR_SCRIPT = """
(function() {
    if (window.__androidClickListenerAttached) {
        return;
    }
    window.__androidClickListenerAttached = true;

    // 클릭해도 본문 추출/저장을 하지 않을, 조작용 요소의 태그 목록.
    var INTERACTIVE_TAGS = { 'BUTTON': true, 'INPUT': true, 'SELECT': true, 'TEXTAREA': true, 'LABEL': true };

    // 클래스 이름에 이 문자열이 포함되어 있으면(대소문자 무관) 드롭다운 등 메뉴 UI로 보고 제외한다.
    function hasDropdownClass(el) {
        var className = (el.className && typeof el.className === 'string') ? el.className : '';
        return className.toLowerCase().indexOf('dropdown') !== -1;
    }

    document.addEventListener('click', function(event) {
        var core = window.__webScraperCore;
        if (!core) {
            return;
        }

        // event.target은 섀도우 DOM 경계에서 리타게팅되어 실제 클릭된 내부 요소가 아니라
        // 섀도우 호스트로 보일 수 있으므로, 판별은 composedPath()로 실제 경로를 본다.
        var path = (typeof event.composedPath === 'function') ? event.composedPath() : [event.target];
        var shouldSkip = false;
        for (var p = 0; p < path.length; p++) {
            var n = path[p];
            if (!n.tagName) {
                continue;
            }
            if (n.tagName === 'A' && n.hasAttribute && n.hasAttribute('href')) {
                shouldSkip = true;
                break;
            }
            if (INTERACTIVE_TAGS[n.tagName]) {
                shouldSkip = true;
                break;
            }
            if (n.getAttribute && (n.getAttribute('role') === 'button' || n.getAttribute('contenteditable') === 'true')) {
                shouldSkip = true;
                break;
            }
            if (hasDropdownClass(n)) {
                shouldSkip = true;
                break;
            }
        }

        if (shouldSkip) {
            return;
        }

        var textNode = event.target;
        var text = '';
        for (var i = 0; i < 6 && textNode; i++) {
            text = core.collectVisibleText(textNode).trim();
            if (text.length > 0) {
                break;
            }
            textNode = textNode.parentElement;
        }

        if (text.length > 0 && window.AndroidBridge && window.AndroidBridge.onTextExtracted) {
            window.AndroidBridge.onTextExtracted(text, core.buildFileNameHint());
        }
    }, true);
})();
"""

/**
 * 매크로(자동 저장) 기능에서 클릭 없이 본문을 추출하기 위해 매 페이지 로딩 후 주입하는 스크립트.
 * CORE_SCRIPT가 먼저 주입되어 있어야 한다(window.__webScraperCore).
 *
 * 실제 본문은 'theme-novel-content' 클래스 요소 안에 있으며, 그 안의 오픈 섀도우 루트가
 * 비동기로 붙고 채워질 수 있으므로, 본문 텍스트가 비어있지 않을 때까지 짧은 간격으로
 * 재시도(polling)한 뒤 결과를 AndroidBridge로 전달한다. (섀도우 루트가 아직 없거나 비어있으면
 * collectVisibleText가 빈 문자열을 반환하므로, 재시도 자체가 "open까지 기다리는" 효과를 낸다.)
 * 일정 횟수 이상 재시도해도 못 찾으면 빈 문자열로 결과를 보내 호출 측이 실패로 처리할 수 있게 한다.
 */
const val AUTO_EXTRACT_SCRIPT = """
(function() {
    var core = window.__webScraperCore;
    if (!core) {
        return;
    }

    // 같은 페이지에 이 스크립트가 다시 주입되더라도(예: onPageFinished 중복 호출) 이전에
    // 시작된 polling은 무시하도록 매번 새 토큰을 발급해서 가장 최근 것만 유효하게 한다.
    var token = {};
    window.__autoExtractToken = token;

    var MAX_ATTEMPTS = 25;
    var DELAY_MS = 200;
    var attempts = 0;

    function tryExtract() {
        if (window.__autoExtractToken !== token) {
            return;
        }

        var contentEl = document.querySelector('.theme-novel-content');
        var text = contentEl ? core.collectVisibleText(contentEl).trim() : '';

        attempts++;
        if (text.length > 0) {
            if (window.AndroidBridge && window.AndroidBridge.onAutoExtractResult) {
                window.AndroidBridge.onAutoExtractResult(text, core.buildFileNameHint());
            }
            return;
        }

        if (attempts >= MAX_ATTEMPTS) {
            if (window.AndroidBridge && window.AndroidBridge.onAutoExtractResult) {
                // 끝까지 본문을 찾지 못했어도 빈 문자열로 알려줘서, 호출 측(ViewModel)이
                // 실패로 처리하고 다음 회차로 넘어갈 수 있게 한다.
                window.AndroidBridge.onAutoExtractResult('', core.buildFileNameHint());
            }
            return;
        }

        setTimeout(tryExtract, DELAY_MS);
    }

    tryExtract();
})();
"""

/** WebView에 addJavascriptInterface로 등록할 때 사용하는 이름. JS 스크립트의 window.AndroidBridge와 일치해야 한다. */
const val JS_BRIDGE_NAME = "AndroidBridge"
