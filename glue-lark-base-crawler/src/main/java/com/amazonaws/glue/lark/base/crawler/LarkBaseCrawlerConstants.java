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
public final class LarkBaseCrawlerConstants
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

    /**
     * Safety valve on top of cycle detection for chained LOOKUP resolution
     * (see {@link com.amazonaws.glue.lark.base.crawler.BaseLarkBaseCrawlerHandler#getLookupType}) - caps how many
     * hops are followed even for a legitimate, non-circular chain. Matches
     * athena-lark-base's BaseConstants.DEFAULT_LARK_LOOKUP_MAX_DEPTH.
     */
    public static final int LOOKUP_MAX_DEPTH = 20;

    /**
     * When set to "true", every column that would otherwise be crawled as a Glue {@code array<...>}/
     * {@code struct<...>} type (MULTI_SELECT, USER, GROUP_CHAT, ATTACHMENT, CREATED_USER, MODIFIED_USER,
     * LOOKUP, URL, LOCATION, SINGLE_LINK, DUPLEX_LINK) is instead crawled as a plain {@code string} column
     * holding a JSON-serialized representation of the same value. Opt-in and off by default - existing
     * tables/queries that rely on List/Struct-typed columns are unaffected unless this is explicitly set.
     * <p>
     * Exists because any WHERE constraint (including IS NOT NULL) referencing a List/Struct-typed column
     * crashes the whole query inside Amazon Athena's own managed query engine
     * ({@code IllegalArgumentException: Lists have one child Field. Found: none}, from Apache Arrow's
     * {@code ListVector.initializeChildrenFromFields}) - a genuine platform-level limitation, not something
     * fixable by changing what this connector returns while the column stays List/Struct-typed. Representing
     * the value as a JSON string instead sidesteps the crash entirely, at the cost of losing native
     * array/struct access in Athena (callers must parse the JSON string themselves, e.g. via Trino/Presto's
     * {@code json_extract}).
     * <p>
     * Matches {@code athena-lark-base}'s identical env var name/semantics - set the same value on both the
     * crawler and connector Lambdas so the schema this crawls into Glue agrees with how the connector
     * would build the same schema for a live (non-crawled) source.
     */
    public static final String ACTIVATE_COMPLEX_TYPE_AS_JSON_STRING_ENV_VAR = "default_does_activate_complex_type_as_json_string";

    /**
     * Private constructor to prevent instantiation.
     */
    private LarkBaseCrawlerConstants()
    {
        // This class is not meant to be instantiated.
    }
}
