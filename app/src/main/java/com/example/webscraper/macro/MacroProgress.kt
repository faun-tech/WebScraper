package com.example.webscraper.macro

/** 매크로(자동 회차 저장) 진행 상태. */
data class MacroProgress(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val savedCount: Int = 0,
    val failedCount: Int = 0
)
