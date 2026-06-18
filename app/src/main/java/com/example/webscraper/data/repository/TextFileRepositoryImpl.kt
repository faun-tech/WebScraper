package com.example.webscraper.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class TextFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TextFileRepository {

    override suspend fun saveText(uri: Uri, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("출력 스트림을 열 수 없습니다.")
                outputStream.use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
}
