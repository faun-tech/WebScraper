package com.example.webscraper.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.webscraper.data.model.TextFileEntry
import com.example.webscraper.util.NaturalOrderComparator
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

    override suspend fun saveTextToFolder(folderUri: Uri, fileName: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                    ?: throw IOException("폴더를 열 수 없습니다.")
                val newFile = folder.createFile("text/plain", fileName)
                    ?: throw IOException("파일을 생성할 수 없습니다.")
                val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                    ?: throw IOException("출력 스트림을 열 수 없습니다.")
                outputStream.use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }

    override suspend fun listTextFiles(folderUri: Uri): Result<List<TextFileEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                    ?: throw IOException("폴더를 열 수 없습니다.")
                folder.listFiles()
                    .filter { it.isFile && isTextFile(it.name) }
                    .mapNotNull { file ->
                        val name = file.name ?: return@mapNotNull null
                        TextFileEntry(uri = file.uri, name = name)
                    }
                    .sortedWith(compareBy(NaturalOrderComparator) { it.name })
            }
        }

    override suspend fun readText(uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("입력 스트림을 열 수 없습니다.")
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }

    private fun isTextFile(name: String?): Boolean {
        return name?.endsWith(".txt", ignoreCase = true) == true
    }
}
