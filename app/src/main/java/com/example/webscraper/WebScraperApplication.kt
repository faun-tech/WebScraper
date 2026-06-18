package com.example.webscraper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 클래스. Hilt의 DI 그래프 루트 역할을 한다.
 */
@HiltAndroidApp
class WebScraperApplication : Application()
