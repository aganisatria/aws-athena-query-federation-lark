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
package com.amazonaws.glue.lark.base.crawler.model.enums;

/**
 * Enum for UI Type
 */
public enum UITypeEnum
{
    TEXT("Text"),
    BARCODE("Barcode"),
    SINGLE_SELECT("SingleSelect"),
    PHONE("Phone"),
    NUMBER("Number"),
    AUTO_NUMBER("AutoNumber"),
    PROGRESS("Progress"),
    CURRENCY("Currency"),
    RATING("Rating"),
    MULTI_SELECT("MultiSelect"),
    USER("User"),
    GROUP_CHAT("GroupChat"),
    ATTACHMENT("Attachment"),
    FORMULA("Formula"),
    SINGLE_LINK("SingleLink"),
    DUPLEX_LINK("DuplexLink"),
    DATE_TIME("DateTime"),
    CREATED_TIME("CreatedTime"),
    MODIFIED_TIME("ModifiedTime"),
    CHECKBOX("Checkbox"),
    URL("Url"),
    LOCATION("Location"),
    CREATED_USER("CreatedUser"),
    MODIFIED_USER("ModifiedUser"),
    EMAIL("Email"),
    LOOKUP("Lookup"), //Type: 19

    // Unavailable API at the moment
    BUTTON("Button"), //Type: 3001
    STAGE("Stage"), //Type: 24

    UNKNOWN("unknown");

    private final String uiType;

    UITypeEnum(String uiType)
    {
        this.uiType = uiType;
    }

    public String getUiType()
    {
        return uiType;
    }

    public String getGlueCatalogType(String customType)
    {
        return switch (this) {
            // TEXT: based on try, any UTF-8 character will be stored as string
            // PHONE: in front and number, we use string because it is more flexible
            // BARCODE: in front and number, we use string because it is more flexible
            // SINGLE_SELECT: we use string because it is more flexible
            // AUTO_NUMBER: we use string because there is like custom prefix for example "INV-"
            case TEXT, BARCODE, SINGLE_SELECT, PHONE, AUTO_NUMBER, EMAIL -> "string";
            // PROGRESS: value between 0 - 100, we use decimal because it is more efficient
            case NUMBER, PROGRESS, CURRENCY -> "decimal";
            // RATING: // value between 0 - 10, we use tinyint because it is more efficient
            case RATING -> "tinyint";
            // ["ab", "12", "cd", "@"] -> array of string
            case MULTI_SELECT -> "array<string>";
            // [{ "avatar_url": "https://...", -> array of struct
            //    "email": "test@company.com",
            //    "en_name": "Agani Satria",
            //    "id": "ou_12345",
            //    "name": "Agani Satria" }],
            case USER -> "array<struct<avatar_url:string,email:string,en_name:string,id:string,name:string>>";
            // [{ "avatar_url": "https://example.com/avatar.webp", -> array of struct
            //    "id": "oc_12345",
            //    "name": "Agani Satria" }]
            case GROUP_CHAT -> "array<struct<avatar_url:string,id:string,name:string>>";
            // [{ "file_token": "test", -> array of struct
            //    "name": "Test.JPG",
            //    "size": int,
            //    "tmp_url": "https://example.com/test",
            //    "type": "image/jpeg",
            //    "url": "https://example.com/test" }]
            case ATTACHMENT ->
                    "array<struct<file_token:string,name:string,size:int,tmp_url:string,type:string,url:string>>";
            case FORMULA -> customType != null ? customType : "string";
            case LOOKUP -> customType != null ? "array<" + customType + ">" : "array<string>";
            // Search API format for LINK fields (returns as Map, not Array)
            // { "link_record_ids": ["rec_xxx", "rec_yyy"] }
            case SINGLE_LINK, DUPLEX_LINK ->
                    "struct<link_record_ids:array<string>>";
            case DATE_TIME, CREATED_TIME, MODIFIED_TIME -> "timestamp";
            case CHECKBOX -> "boolean";
            // {"link": "https://aganisatria.vercel.app",
            //  "text": "test",
            //  "type": "url"}
            case URL -> "struct<link:string,text:string,type:string>";
            // { "address": "dummy",
            //   "adname": "",
            //   "cityname": "",
            //   "full_address": "dummy",
            //   "location": "1.0,1.0",
            //   "name": "dummy",
            //   "pname": "" }
            case LOCATION ->
                    "struct<address:string,adname:string,cityname:string,full_address:string,location:string,name:string,pname:string>";
            // CREATED_USER and MODIFIED_USER return as array with single element
            // [{ "avatar_url": "https://...",
            //    "email": "test@example.com",
            //    "en_name": "John Doe",
            //    "id": "ou_12345",
            //    "name": "John Doe" }]
            case CREATED_USER, MODIFIED_USER -> "array<struct<avatar_url:string,email:string,en_name:string,id:string,name:string>>";
            default -> "string";
        };
    }

    public static UITypeEnum fromString(String text)
    {
        for (UITypeEnum uiType : UITypeEnum.values()) {
            if (uiType.getUiType().equalsIgnoreCase(text)) {
                return uiType;
            }
        }
        return UNKNOWN;
    }
}
