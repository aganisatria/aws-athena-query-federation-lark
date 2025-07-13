/*-
 * #%L
 * glue-lark-base-crawler
 * %%
 * Copyright (C) 2019 - 2025 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.amazonaws.glue.lark.base.crawler.model.response;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ListAllFolderResponseTest {

    @Test
    public void driveFile_builderAndGetters_shouldWork() {
        String name = "My Folder @Docs";
        String sanitizedName = "my_folder__docs";
        String parentToken = "parentTkn";
        String token = "fileTkn";
        String type = "folder";

        ListAllFolderResponse.DriveFile driveFile = ListAllFolderResponse.DriveFile.builder()
                .name(name)
                .parentToken(parentToken)
                .token(token)
                .type(type)
                .build();

        assertEquals(sanitizedName, driveFile.getName());
        assertEquals(name, driveFile.getRawName());
        assertEquals(parentToken, driveFile.getParentToken());
        assertEquals(token, driveFile.getToken());
        assertEquals(type, driveFile.getType());
    }

    @Test
    public void listData_builderAndGetters_shouldWork() {
        ListAllFolderResponse.DriveFile file1 = ListAllFolderResponse.DriveFile.builder().name("Folder A").build();
        List<ListAllFolderResponse.DriveFile> files = Collections.singletonList(file1);
        String nextPageToken = "nextPageTkn123";
        boolean hasMore = true;

        ListAllFolderResponse.ListData listData = ListAllFolderResponse.ListData.builder()
                .files(files)
                .nextPageToken(nextPageToken)
                .hasMore(hasMore)
                .build();

        assertEquals(files, listData.getFiles());
        assertEquals(nextPageToken, listData.getNextPageToken());
        assertEquals(hasMore, listData.hasMore());
    }

    @Test
    public void listData_files_whenBuilderFilesIsNull_shouldReturnEmptyList() {
        ListAllFolderResponse.ListData listData = ListAllFolderResponse.ListData.builder()
                .files(null)
                .build();
        assertNotNull(listData.getFiles());
        assertTrue(listData.getFiles().isEmpty());
    }

    @Test
    public void listAllFolderResponse_builderAndGetters_shouldWork() {
        ListAllFolderResponse.DriveFile file1 = ListAllFolderResponse.DriveFile.builder().name("Folder A").build();
        List<ListAllFolderResponse.DriveFile> files = Collections.singletonList(file1);
        ListAllFolderResponse.ListData listData = ListAllFolderResponse.ListData.builder()
                .files(files)
                .nextPageToken("nextPage")
                .hasMore(true)
                .build();

        ListAllFolderResponse response = (ListAllFolderResponse) ListAllFolderResponse.builder()
                .code(0)
                .msg("ok")
                .data(listData)
                .build();

        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMsg());
        assertNotNull(response.getData());
        assertEquals(files, response.getFiles());
        assertEquals("nextPage", response.getNextPageToken());
        assertTrue(response.hasMore());
    }

    @Test
    public void listAllFolderResponse_getters_whenDataIsNull_shouldReturnDefaults() {
        ListAllFolderResponse response = (ListAllFolderResponse) ListAllFolderResponse.builder().data(null).build();

        assertNotNull(response.getFiles());
        assertTrue(response.getFiles().isEmpty());
        assertNull(response.getNextPageToken());
        assertFalse(response.hasMore());
    }
}
