package com.example.webscraper.macro

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 매크로 진행 상태를 ViewModel(UI 표시)과 MacroForegroundService(실제 진행) 사이에 공유하기
 * 위한 싱글톤. 실제로 회차를 순회하며 진행 상태를 갱신하는 쪽은 서비스이고, ViewModel/Activity는
 * 이 값을 구독해서 상태바에 표시만 한다. Activity가 화면에서 사라지거나 STARTED 상태를 벗어나도
 * 서비스는 이 값을 계속 갱신하며, Activity가 다시 보이면 StateFlow의 최신 값을 그대로 받는다.
 */
@Singleton
class MacroProgressHolder @Inject constructor() {

    private val _progress = MutableStateFlow(MacroProgress())
    val progress: StateFlow<MacroProgress> = _progress.asStateFlow()

    fun update(progress: MacroProgress) {
        _progress.value = progress
    }

    fun reset() {
        _progress.value = MacroProgress()
    }
}
