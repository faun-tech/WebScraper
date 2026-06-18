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

    /**
     * [folderUri](Storage Access Framework로 사용자가 선택한 폴더) 안에 [fileName] 이름의
     * 새 파일을 만들어 [text]를 UTF-8로 작성한다. 매크로처럼 한 번 폴더를 선택한 뒤
     * 여러 파일을 연달아 저장할 때 사용한다.
     */
    suspend fun saveTextToFolder(folderUri: Uri, fileName: String, text: String): Result<Unit>
}
