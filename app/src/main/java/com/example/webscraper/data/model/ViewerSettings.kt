package com.example.webscraper.data.model

/** 뷰어 배경색 프리셋. */
enum class ViewerBackgroundPreset(val label: String, val colorHex: String) {
    WHITE("흰색", "#FFFFFF"),
    SEPIA("세피아", "#F5ECD3"),
    MINT("연한 민트", "#E3F2E1"),
    DARK_GRAY("다크 그레이", "#1E1E1E"),
    BLACK("검정", "#000000")
}

/** 뷰어 글자색 프리셋. */
enum class ViewerTextColorPreset(val label: String, val colorHex: String) {
    BLACK("검정", "#000000"),
    DARK_GRAY("진회색", "#333333"),
    SEPIA_BROWN("세피아 갈색", "#5B4636"),
    LIGHT_GRAY("연회색", "#CCCCCC"),
    WHITE("흰색", "#FFFFFF")
}

/** 뷰어 글자 크기 프리셋. */
enum class ViewerTextSizePreset(val label: String, val sp: Float) {
    SMALL("작게", 14f),
    MEDIUM("보통", 18f),
    LARGE("크게", 22f),
    EXTRA_LARGE("아주 크게", 26f)
}

/** 텍스트 뷰어 화면에 적용되는 표시 설정 한 묶음. */
data class ViewerSettings(
    val background: ViewerBackgroundPreset = ViewerBackgroundPreset.WHITE,
    val textColor: ViewerTextColorPreset = ViewerTextColorPreset.BLACK,
    val textSize: ViewerTextSizePreset = ViewerTextSizePreset.MEDIUM
)
