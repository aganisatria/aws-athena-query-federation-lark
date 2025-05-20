package com.amazonaws.glue.lark.base.crawler.model.enums;

import org.junit.Test;
import static org.junit.Assert.*;

public class UITypeEnumTest {

    @Test
    public void fromString_validTypes_shouldReturnCorrectEnum() {
        assertEquals(UITypeEnum.TEXT, UITypeEnum.fromString("Text"));
        assertEquals(UITypeEnum.NUMBER, UITypeEnum.fromString("Number"));
        assertEquals(UITypeEnum.MULTI_SELECT, UITypeEnum.fromString("MultiSelect"));
        assertEquals(UITypeEnum.LOOKUP, UITypeEnum.fromString("Lookup"));
        assertEquals(UITypeEnum.FORMULA, UITypeEnum.fromString("Formula"));
    }

    @Test
    public void fromString_caseInsensitive_shouldReturnCorrectEnum() {
        assertEquals(UITypeEnum.TEXT, UITypeEnum.fromString("text"));
        assertEquals(UITypeEnum.TEXT, UITypeEnum.fromString("TEXT"));
        assertEquals(UITypeEnum.PHONE, UITypeEnum.fromString("pHONe"));
    }

    @Test
    public void fromString_invalidType_shouldReturnUnknown() {
        assertEquals(UITypeEnum.UNKNOWN, UITypeEnum.fromString("NonExistentType"));
    }

    @Test
    public void fromString_emptyString_shouldReturnUnknown() {
        assertEquals(UITypeEnum.UNKNOWN, UITypeEnum.fromString(""));
    }

    @Test
    public void fromString_nullString_shouldReturnUnknown() {
        assertEquals(UITypeEnum.UNKNOWN, UITypeEnum.fromString(null));
    }

    @Test
    public void getUiType_shouldReturnCorrectStringValue() {
        assertEquals("Text", UITypeEnum.TEXT.getUiType());
        assertEquals("MultiSelect", UITypeEnum.MULTI_SELECT.getUiType());
        assertEquals("unknown", UITypeEnum.UNKNOWN.getUiType());
    }

    @Test
    public void getGlueCatalogType_textTypes_shouldReturnString() {
        assertEquals("string", UITypeEnum.TEXT.getGlueCatalogType(null));
        assertEquals("string", UITypeEnum.BARCODE.getGlueCatalogType(null));
        assertEquals("string", UITypeEnum.SINGLE_SELECT.getGlueCatalogType(null));
        assertEquals("string", UITypeEnum.PHONE.getGlueCatalogType(null));
        assertEquals("string", UITypeEnum.AUTO_NUMBER.getGlueCatalogType(null));
        assertEquals("string", UITypeEnum.EMAIL.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_numberTypes_shouldReturnDecimal() {
        assertEquals("decimal", UITypeEnum.NUMBER.getGlueCatalogType(null));
        assertEquals("decimal", UITypeEnum.PROGRESS.getGlueCatalogType(null));
        assertEquals("decimal", UITypeEnum.CURRENCY.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_rating_shouldReturnTinyInt() {
        assertEquals("tinyint", UITypeEnum.RATING.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_multiSelect_shouldReturnArrayString() {
        assertEquals("array<string>", UITypeEnum.MULTI_SELECT.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_user_shouldReturnUserStruct() {
        String expected = "array<struct<email:string,en_name:string,id:string,name:string>>";
        assertEquals(expected, UITypeEnum.USER.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_groupChat_shouldReturnGroupChatStruct() {
        String expected = "array<struct<avatar_url:string,id:string,name:string>>";
        assertEquals(expected, UITypeEnum.GROUP_CHAT.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_attachment_shouldReturnAttachmentStruct() {
        String expected = "array<struct<file_token:string,name:string,size:int,tmp_url:string,type:string,url:string>>";
        assertEquals(expected, UITypeEnum.ATTACHMENT.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_formula_withCustomType() {
        assertEquals("int", UITypeEnum.FORMULA.getGlueCatalogType("int"));
    }

    @Test
    public void getGlueCatalogType_formula_withoutCustomType_shouldDefaultToString() {
        assertEquals("string", UITypeEnum.FORMULA.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_lookup_withCustomType() {
        assertEquals("array<struct<field:string>>", UITypeEnum.LOOKUP.getGlueCatalogType("struct<field:string>"));
    }

    @Test
    public void getGlueCatalogType_lookup_withoutCustomType_shouldDefaultToArrayString() {
        assertEquals("array<string>", UITypeEnum.LOOKUP.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_linkTypes_shouldReturnLinkStruct() {
        String expected = "array<struct<record_ids:array<string>,table_id:string,text:string,text_arr:array<string>,type:string>>";
        assertEquals(expected, UITypeEnum.SINGLE_LINK.getGlueCatalogType(null));
        assertEquals(expected, UITypeEnum.DUPLEX_LINK.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_dateTimeTypes_shouldReturnTimestamp() {
        assertEquals("timestamp", UITypeEnum.DATE_TIME.getGlueCatalogType(null));
        assertEquals("timestamp", UITypeEnum.CREATED_TIME.getGlueCatalogType(null));
        assertEquals("timestamp", UITypeEnum.MODIFIED_TIME.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_checkbox_shouldReturnBoolean() {
        assertEquals("boolean", UITypeEnum.CHECKBOX.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_url_shouldReturnUrlStruct() {
        assertEquals("struct<link:string,text:string>", UITypeEnum.URL.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_location_shouldReturnLocationStruct() {
        String expected = "struct<address:string,adname:string,cityname:string,full_address:string,location:string,name:string,pname:string>";
        assertEquals(expected, UITypeEnum.LOCATION.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_createdModifiedUser_shouldReturnUserStructSingle() {
        String expected = "struct<id:string,name:string,en_name:string,email:string>";
        assertEquals(expected, UITypeEnum.CREATED_USER.getGlueCatalogType(null));
        assertEquals(expected, UITypeEnum.MODIFIED_USER.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_buttonAndStage_shouldReturnString() {
        assertEquals("string", UITypeEnum.BUTTON.getGlueCatalogType(null));
        assertEquals("string", UITypeEnum.STAGE.getGlueCatalogType(null));
    }

    @Test
    public void getGlueCatalogType_unknown_shouldReturnString() {
        assertEquals("string", UITypeEnum.UNKNOWN.getGlueCatalogType(null));
    }
}