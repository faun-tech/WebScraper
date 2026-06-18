package com.example.webscraper.di

import com.example.webscraper.data.repository.TextFileRepository
import com.example.webscraper.data.repository.TextFileRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository 인터페이스와 구현체를 바인딩하는 Hilt 모듈.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindTextFileRepository(
        impl: TextFileRepositoryImpl
    ): TextFileRepository
}
