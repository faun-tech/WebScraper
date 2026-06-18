package com.example.webscraper.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 추출 결과로 받은 [suggestedFileName] 힌트를 적절한 .txt 파일명으로 만든다.
 * 비어있으면 타임스탬프 기반 이름으로 대체한다.
 * 클릭 추출(MainViewModel) / 매크로 자동 추출(MacroForegroundService) 양쪽에서 함께 쓴다.
 */
fun buildTextFileName(suggestedFileName: String): String {
    val sanitized = sanitizeFileName(suggestedFileName)
    val base = sanitized.ifBlank {
        "extracted_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }
    return "$base.txt"
}

/** 줄바꿈/공백을 정리하고, 파일명으로 쓸 수 없는 문자를 치환하며, 길이를 제한한다. */
fun sanitizeFileName(raw: String): String {
    return raw
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(80)
        .trim()
}
