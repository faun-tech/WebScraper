package com.example.webscraper.ui.viewer

import com.example.webscraper.data.model.TextFileEntry
import com.example.webscraper.data.model.ViewerSettings

/**
 * 텍스트 뷰어 화면의 전체 상태.
 */
data class TextViewerUiState(
    val files: List<TextFileEntry> = emptyList(),
    val currentIndex: Int = -1,
    val currentText: String = "",
    val isLoadingFolder: Boolean = true,
    val isLoadingFile: Boolean = false,
    val settings: ViewerSettings = ViewerSettings()
) {
    val currentFile: TextFileEntry?
        get() = files.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNext: Boolean
        get() = files.isNotEmpty() && currentIndex in 0 until files.lastIndex

    /** 상단바에 "3 / 12" 처럼 표시할 위치 텍스트. */
    val positionLabel: String
        get() = if (files.isEmpty()) "" else "${currentIndex + 1} / ${files.size}"
}
