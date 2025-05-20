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

import com.amazonaws.glue.lark.base.crawler.model.enums.HandlerTypeEnum;
import com.amazonaws.glue.lark.base.crawler.model.request.MainLarkBasePayload;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Main class for Lark Base Crawler Handler, use this if you want to handle multiple types of requests
 */
public class MainLarkBaseCrawlerHandler implements RequestHandler<Object, String> {

    private static final Logger logger = LoggerFactory.getLogger(MainLarkBaseCrawlerHandler.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final LarkBaseCrawlerHandler larkBaseCrawlerHandler;
    private final LarkDriveCrawlerHandler larkDriveCrawlerHandler;

    public MainLarkBaseCrawlerHandler() {
        this(new LarkBaseCrawlerHandler(), new LarkDriveCrawlerHandler());
    }

    MainLarkBaseCrawlerHandler(LarkBaseCrawlerHandler larkBaseCrawlerHandler, LarkDriveCrawlerHandler larkDriveCrawlerHandler) {
        this.larkBaseCrawlerHandler = Objects.requireNonNull(larkBaseCrawlerHandler, "larkBaseCrawlerHandler must not be null");
        this.larkDriveCrawlerHandler = Objects.requireNonNull(larkDriveCrawlerHandler, "larkDriveCrawlerHandler must not be null");
    }

    @Override
    public String handleRequest(Object input, Context context) {
        MainLarkBasePayload convertInput = OBJECT_MAPPER.convertValue(input, MainLarkBasePayload.class);

        if (Objects.requireNonNull(HandlerTypeEnum.fromString(convertInput.handlerType())) == HandlerTypeEnum.LARK_BASE) {
            logger.info("Handling Lark Base Crawler Request");
            return larkBaseCrawlerHandler.handleRequest(convertInput.payload(), context);
        } else if (Objects.requireNonNull(HandlerTypeEnum.fromString(convertInput.handlerType())) == HandlerTypeEnum.LARK_DRIVE) {
            logger.info("Handling Lark Drive Crawler Request");
            return larkDriveCrawlerHandler.handleRequest(convertInput.payload(), context);
        }
        throw new IllegalArgumentException("Invalid handler type: " + convertInput.handlerType());
    }

}
