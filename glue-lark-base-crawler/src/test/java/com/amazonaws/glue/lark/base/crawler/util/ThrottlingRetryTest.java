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
package com.amazonaws.glue.lark.base.crawler.util;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThrottlingRetryTest {

    @Test
    public void isThrottlingException_matchesAllKnownLarkThrottlingCodesAndMessages() {
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("...Code: 1254290, Error: ...")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("TooManyRequest")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("...Code: 1254291, Error: ...")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("Write conflict")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("...Code: 1255040, Error: ...")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("Request timed out")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("...Code: 1254607, Error: ...")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("Data not ready")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("...Code: 1254036, Error: ...")));
        assertTrue(ThrottlingRetry.isThrottlingException(new IOException("Base is copying")));
    }

    @Test
    public void isThrottlingException_unrelatedErrorDoesNotMatch() {
        assertFalse(ThrottlingRetry.isThrottlingException(new IOException("...Code: 10001, Error: some other failure")));
        assertFalse(ThrottlingRetry.isThrottlingException(new RuntimeException("NOTEXIST")));
    }

    @Test
    public void isThrottlingException_nullMessageDoesNotMatch() {
        assertFalse(ThrottlingRetry.isThrottlingException(new RuntimeException((String) null)));
    }

    @Test
    public void invoke_nonThrottlingException_propagatesImmediatelyWithoutRetry() {
        AtomicInteger calls = new AtomicInteger(0);
        ThrottlingRetry retry = new ThrottlingRetry(10, 1000, 0.5, 10, 60_000);

        try {
            retry.invoke(() -> {
                calls.incrementAndGet();
                throw new IOException("Code: 10001, Error: not a throttling error");
            });
            fail("Expected the exception to propagate");
        }
        catch (Exception e) {
            assertEquals("A non-throttling exception must not be retried", 1, calls.get());
        }
    }

    @Test
    public void invoke_throttlingExceptionThenSuccess_retriesAndReturnsResult() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        // Zero delays so the test runs fast while still exercising the retry loop itself.
        ThrottlingRetry retry = new ThrottlingRetry(0, 0, 0.5, 0, 60_000);

        String result = retry.invoke(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IOException("Code: 1254290, Error: TooManyRequest");
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals("Expected exactly 2 failed attempts before succeeding on the 3rd", 3, calls.get());
    }

    @Test
    public void invoke_timeoutLessThanOrEqualZero_neverRetries() {
        AtomicInteger calls = new AtomicInteger(0);
        ThrottlingRetry retry = new ThrottlingRetry(10, 1000, 0.5, 10, 0);

        try {
            retry.invoke(() -> {
                calls.incrementAndGet();
                throw new IOException("Code: 1254290, Error: TooManyRequest");
            });
            fail("Expected the throttling exception to propagate once the (zero) retry budget is exhausted");
        }
        catch (Exception e) {
            assertTrue(ThrottlingRetry.isThrottlingException(e));
            assertEquals("timeoutMs <= 0 must mean exactly one attempt, no retries", 1, calls.get());
        }
    }

    @Test
    public void reset_clearsAccumulatedBackoffDelay() throws Exception {
        // Ramp up a real backoff delay via one throttling event then a success - handleSuccess only
        // decays by increaseMs (10ms), leaving ~190ms still outstanding, mirroring the real state left
        // behind after a throttling burst that this Lambda-reused instance would otherwise carry into
        // the next, unrelated invocation (see ThrottlingRetry.reset()'s javadoc).
        ThrottlingRetry retry = new ThrottlingRetry(200, 1000, 0.5, 10, 60_000);
        AtomicInteger calls = new AtomicInteger(0);
        retry.invoke(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("Code: 1254290, Error: TooManyRequest");
            }
            return "ok";
        });

        retry.reset();

        long start = System.currentTimeMillis();
        retry.invoke(() -> "ok");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("Expected near-zero delay after reset() (would be ~190ms otherwise), took " + elapsed + "ms",
                elapsed < 100);
    }

    @Test
    public void invoke_persistentThrottling_eventuallyGivesUpAndRethrows() {
        AtomicInteger calls = new AtomicInteger(0);
        // A tiny positive timeout with zero delay still allows at least one retry loop iteration
        // before the elapsed-time check trips, without the test needing to wait long.
        ThrottlingRetry retry = new ThrottlingRetry(0, 0, 0.5, 0, 1);

        try {
            retry.invoke(() -> {
                calls.incrementAndGet();
                throw new IOException("Code: 1254290, Error: TooManyRequest");
            });
            fail("Expected the persistent throttling exception to eventually propagate");
        }
        catch (Exception e) {
            assertTrue(ThrottlingRetry.isThrottlingException(e));
            assertTrue("Expected at least one retry attempt before giving up", calls.get() >= 1);
        }
    }
}
