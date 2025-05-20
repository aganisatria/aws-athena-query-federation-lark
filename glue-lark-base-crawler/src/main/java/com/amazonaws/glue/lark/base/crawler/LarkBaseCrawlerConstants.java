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
package com.amazonaws.glue.lark.base.crawler;

/**
 * Constants for Lark Base Crawler
 */
public class LarkBaseCrawlerConstants
{
    /**
     * The secret manager lark app key which is used to identify the lark app key in secret manager.
     */
    public static final String LARK_APP_KEY_ENV_VAR = "default_secret_manager_lark_app_key";

    /**
     * The lark base flag which is used to identify the lark base crawler database on glue catalog.
     */
    public static final String LARK_BASE_FLAG = "lark-base-flag";

    /**
     * The Crawling Method used to differentiate crawling methods.
     */
    public static final String CRAWLING_METHOD = "CrawlingMethod";
}
