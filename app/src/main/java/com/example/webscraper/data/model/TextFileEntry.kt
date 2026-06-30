package com.example.webscraper.data.model

import android.net.Uri

/**
 * 뷰어에서 다루는 폴더 안 텍스트 파일 한 개. (Storage Access Framework Document URI 기반)
 */
data class TextFileEntry(
    val uri: Uri,
    val name: String
)
