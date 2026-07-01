package com.example.webscraper.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * "이동하기" 버튼으로 실제 이동한 URL의 히스토리를 기기에 저장해둔다. 최근에 이동한 URL이
 * 맨 앞에 오고, 같은 URL을 다시 이동하면 기존 위치에서 빼서 맨 앞으로 옮긴다(중복 제거).
 */
class UrlHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 최근 이동한 순서대로(최신이 0번) URL 목록을 반환한다. */
    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    /** [url]을 히스토리 맨 앞에 추가한다. 이미 있던 같은 URL은 제거하고 새로 맨 앞에 둔다. */
    fun addUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        val updated = getHistory().toMutableList()
        updated.remove(trimmed)
        updated.add(0, trimmed)
        if (updated.size > MAX_HISTORY_SIZE) {
            updated.subList(MAX_HISTORY_SIZE, updated.size).clear()
        }
        prefs.edit().putString(KEY_HISTORY, updated.joinToString(SEPARATOR)).apply()
    }

    /** 히스토리를 모두 비운다. */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val PREFS_NAME = "url_history_prefs"
        const val KEY_HISTORY = "history"
        // URL 문자열에는 줄바꿈이 올 수 없으므로 구분자로 안전하게 쓸 수 있다.
        const val SEPARATOR = "\n"
        const val MAX_HISTORY_SIZE = 50
    }
}
