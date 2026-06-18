package com.example.webscraper.data.repository

import android.net.Uri

/**
 * 추출된 텍스트를 사용자가 지정한 위치에 txt 파일로 저장하는 역할을 담당한다.
 */
interface TextFileRepository {

    /**
     * [uri]가 가리키는 위치(Storage Access Framework로 사용자가 선택한 문서)에
     * [text]를 UTF-8로 작성한다.
     */
    suspend fun saveText(uri: Uri, text: String): Result<Unit>
}
