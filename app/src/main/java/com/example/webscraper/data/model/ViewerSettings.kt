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

/**
 * 뷰어 글자 크기 단계. 12sp ~ 32sp까지 2sp 간격으로 11단계를 둬서 세밀하게 고를 수 있게 한다.
 * 선언 순서가 곧 작은 글자 -> 큰 글자 순서이므로(슬라이더가 이 순서를 그대로 쓴다),
 * 새 단계를 추가할 때도 크기 순서를 지켜 선언해야 한다.
 */
enum class ViewerTextSizePreset(val sp: Float) {
    SP_12(12f),
    SP_14(14f),
    SP_16(16f),
    SP_18(18f),
    SP_20(20f),
    SP_22(22f),
    SP_24(24f),
    SP_26(26f),
    SP_28(28f),
    SP_30(30f),
    SP_32(32f);

    companion object {
        val DEFAULT = SP_18
    }
}

/** 텍스트 뷰어 화면에 적용되는 표시 설정 한 묶음. */
data class ViewerSettings(
    val background: ViewerBackgroundPreset = ViewerBackgroundPreset.WHITE,
    val textColor: ViewerTextColorPreset = ViewerTextColorPreset.BLACK,
    val textSize: ViewerTextSizePreset = ViewerTextSizePreset.DEFAULT
)
