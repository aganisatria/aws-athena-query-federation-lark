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

import com.amazonaws.services.lambda.runtime.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MainLarkBaseCrawlerHandlerTest {

    @Mock
    private LarkBaseCrawlerHandler mockLarkBaseCrawlerHandler;

    @Mock
    private LarkDriveCrawlerHandler mockLarkDriveCrawlerHandler;

    @Mock
    private Context mockContext;

    private MainLarkBaseCrawlerHandler mainLarkBaseCrawlerHandler;

    @Before
    public void setUp() {
        mainLarkBaseCrawlerHandler = new MainLarkBaseCrawlerHandler(mockLarkBaseCrawlerHandler, mockLarkDriveCrawlerHandler);
    }

    @Test
    public void handleRequest_shouldCallLarkBaseCrawlerHandler_whenHandlerTypeIsLarkBase() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("someBaseKey", "someBaseValue");

        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("handlerType", "LARK_BASE");
        inputMap.put("payload", payload);

        String expectedResult = "LarkBaseSuccess";
        when(mockLarkBaseCrawlerHandler.handleRequest(any(), eq(mockContext))).thenReturn(expectedResult);

        String actualResult = mainLarkBaseCrawlerHandler.handleRequest(inputMap, mockContext);

        assertEquals(expectedResult, actualResult);
        verify(mockLarkBaseCrawlerHandler).handleRequest(any(), eq(mockContext));
    }

    @Test
    public void handleRequest_shouldCallLarkDriveCrawlerHandler_whenHandlerTypeIsLarkDrive() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("someDriveKey", "someDriveValue");

        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("handlerType", "LARK_DRIVE");
        inputMap.put("payload", payload);

        String expectedResult = "LarkDriveSuccess";
        when(mockLarkDriveCrawlerHandler.handleRequest(any(), eq(mockContext))).thenReturn(expectedResult);

        String actualResult = mainLarkBaseCrawlerHandler.handleRequest(inputMap, mockContext);

        assertEquals(expectedResult, actualResult);
        verify(mockLarkDriveCrawlerHandler).handleRequest(any(), eq(mockContext));
    }

    @Test
    public void handleRequest_shouldThrowIllegalArgumentException_whenHandlerTypeIsInvalid() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("someKey", "someValue");

        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("handlerType", "INVALID_TYPE");
        inputMap.put("payload", payload);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mainLarkBaseCrawlerHandler.handleRequest(inputMap, mockContext));

        assertEquals("Invalid handler type: INVALID_TYPE", exception.getMessage());
    }

    @Test
    public void handleRequest_shouldThrowIllegalArgumentException_whenHandlerTypeIsNull() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("handlerType", null);
        inputMap.put("payload", payload);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> mainLarkBaseCrawlerHandler.handleRequest(inputMap, mockContext));
        assertEquals("Invalid handler type: null", exception.getMessage());
    }

    @Test
    public void handleRequest_shouldThrowException_whenPayloadIsIncorrectForConversion() {
        String incorrectInput = "JustAStringNotAMap";

        assertThrows(IllegalArgumentException.class, () -> mainLarkBaseCrawlerHandler.handleRequest(incorrectInput, mockContext));
    }
}
