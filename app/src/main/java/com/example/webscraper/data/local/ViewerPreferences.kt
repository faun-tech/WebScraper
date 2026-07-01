package com.example.webscraper.data.local

import android.content.Context
import android.net.Uri
import com.example.webscraper.data.model.ViewerBackgroundPreset
import com.example.webscraper.data.model.ViewerSettings
import com.example.webscraper.data.model.ViewerTextColorPreset
import com.example.webscraper.data.model.ViewerTextSizePreset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 텍스트 뷰어의 표시 설정(배경색/글자색/글자크기)과, 폴더별 마지막으로 보던 파일·그 파일 안
 * 스크롤 위치를 기기에 저장해둔다. 같은 폴더를 다시 열거나 앱을 다시 켜도 마지막으로 보던
 * 지점이 그대로 유지되도록 한다.
 */
class ViewerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSettings(): ViewerSettings {
        val background = prefs.getString(KEY_BACKGROUND, null)
            ?.let { runCatching { ViewerBackgroundPreset.valueOf(it) }.getOrNull() }
            ?: ViewerBackgroundPreset.WHITE
        val textColor = prefs.getString(KEY_TEXT_COLOR, null)
            ?.let { runCatching { ViewerTextColorPreset.valueOf(it) }.getOrNull() }
            ?: ViewerTextColorPreset.BLACK
        val textSize = prefs.getString(KEY_TEXT_SIZE, null)
            ?.let { runCatching { ViewerTextSizePreset.valueOf(it) }.getOrNull() }
            ?: ViewerTextSizePreset.DEFAULT
        return ViewerSettings(background, textColor, textSize)
    }

    fun saveSettings(settings: ViewerSettings) {
        prefs.edit()
            .putString(KEY_BACKGROUND, settings.background.name)
            .putString(KEY_TEXT_COLOR, settings.textColor.name)
            .putString(KEY_TEXT_SIZE, settings.textSize.name)
            .apply()
    }

    /** [folderUri] 폴더에서 마지막으로 보고 있던 파일의 URI를 반환한다. 없으면 null. */
    fun getLastFileUri(folderUri: Uri): Uri? {
        val stored = prefs.getString(KEY_LAST_FILE_PREFIX + folderUri.toString(), null)
        return stored?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    /** [folderUri] 폴더에서 마지막으로 보던 파일이 [fileUri]였음을 기록한다. */
    fun saveLastFileUri(folderUri: Uri, fileUri: Uri) {
        prefs.edit()
            .putString(KEY_LAST_FILE_PREFIX + folderUri.toString(), fileUri.toString())
            .apply()
    }

    /** [fileUri] 파일을 마지막으로 보던 세로 스크롤 위치(px)를 반환한다. 없으면 0. */
    fun getScrollPosition(fileUri: Uri): Int {
        return prefs.getInt(KEY_SCROLL_PREFIX + fileUri.toString(), 0)
    }

    /** [fileUri] 파일의 세로 스크롤 위치(px)를 [scrollY]로 저장한다. */
    fun saveScrollPosition(fileUri: Uri, scrollY: Int) {
        prefs.edit()
            .putInt(KEY_SCROLL_PREFIX + fileUri.toString(), scrollY)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "text_viewer_prefs"
        const val KEY_BACKGROUND = "background"
        const val KEY_TEXT_COLOR = "text_color"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_LAST_FILE_PREFIX = "last_file_"
        const val KEY_SCROLL_PREFIX = "scroll_"
    }
}
