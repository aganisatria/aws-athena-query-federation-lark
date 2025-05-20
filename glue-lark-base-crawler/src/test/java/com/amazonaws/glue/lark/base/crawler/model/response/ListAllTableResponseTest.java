package com.amazonaws.glue.lark.base.crawler.model.response;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ListAllTableResponseTest {

    @Test
    public void baseItem_builderAndGetters_shouldWork() {
        String tableId = "tbl123";
        String revision = "rev_abc";
        String rawName = "My Table @1";
        String sanitizedName = "my_table__1";

        ListAllTableResponse.BaseItem item = ListAllTableResponse.BaseItem.builder()
                .tableId(tableId)
                .revision(revision)
                .name(rawName)
                .build();

        assertEquals(tableId, item.getTableId());
        assertEquals(revision, item.getRevision());
        assertEquals(sanitizedName, item.getName());
        assertEquals(rawName, item.getRawName());
    }

    @Test
    public void listData_builderAndGetters_shouldWork() {
        ListAllTableResponse.BaseItem item1 = ListAllTableResponse.BaseItem.builder().name("Table A").build();
        List<ListAllTableResponse.BaseItem> items = Collections.singletonList(item1);
        String pageToken = "nextToken123";
        boolean hasMore = true;

        ListAllTableResponse.ListData listData = ListAllTableResponse.ListData.builder()
                .items(items)
                .pageToken(pageToken)
                .hasMore(hasMore)
                .build();

        assertEquals(items, listData.getItems());
        assertEquals(pageToken, listData.getPageToken());
        assertEquals(hasMore, listData.hasMore());
    }

    @Test
    public void listData_items_whenBuilderItemsIsNull_shouldReturnEmptyList() {
        ListAllTableResponse.ListData listData = ListAllTableResponse.ListData.builder()
                .items(null)
                .build();
        assertNotNull(listData.getItems());
        assertTrue(listData.getItems().isEmpty());
    }

    @Test
    public void listAllTableResponse_builderAndGetters_shouldWork() {
        ListAllTableResponse.BaseItem item1 = ListAllTableResponse.BaseItem.builder().name("Table A").build();
        List<ListAllTableResponse.BaseItem> items = Collections.singletonList(item1);
        ListAllTableResponse.ListData listData = ListAllTableResponse.ListData.builder()
                .items(items)
                .pageToken("nextPage")
                .hasMore(true)
                .build();

        ListAllTableResponse response = (ListAllTableResponse) ListAllTableResponse.builder()
                .code(0)
                .msg("ok")
                .data(listData)
                .build();

        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMsg());
        assertNotNull(response.getData());
        assertEquals(items, response.getItems());
        assertEquals("nextPage", response.getPageToken());
        assertTrue(response.hasMore());
    }

    @Test
    public void listAllTableResponse_getters_whenDataIsNull_shouldReturnDefaults() {
        ListAllTableResponse response = (ListAllTableResponse) ListAllTableResponse.builder().data(null).build();

        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());
        assertNull(response.getPageToken());
        assertFalse(response.hasMore());
    }
}