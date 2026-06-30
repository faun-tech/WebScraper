package com.example.webscraper.data.repository

import android.net.Uri
import com.example.webscraper.data.model.TextFileEntry

/**
 * 추출된 텍스트를 사용자가 지정한 위치에 txt 파일로 저장하거나, 이미 저장된 텍스트 파일들을
 * 뷰어에서 읽어들이는 역할을 담당한다.
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

    /**
     * [folderUri] 폴더 안에 있는 텍스트 파일(.txt) 목록을, 파일명 기준 자연스러운 순서
     * (예: 1화, 2화, ..., 10화)로 정렬해 반환한다. 뷰어에서 폴더 안 파일들을 순서대로
     * 넘겨보는 데 사용한다.
     */
    suspend fun listTextFiles(folderUri: Uri): Result<List<TextFileEntry>>

    /** [uri]가 가리키는 텍스트 파일의 내용을 UTF-8로 읽어들인다. */
    suspend fun readText(uri: Uri): Result<String>
}
