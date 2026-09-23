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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Retries a Lark API call with Additive-Increase/Multiplicative-Decrease backoff when it hits Lark's
 * rate limiting (or another transient, congestion-shaped error). Before this class existed, none of the
 * crawler's Lark API call sites had any retry/backoff at all - unlike athena-lark-base's connector
 * module, which has this exact protection via aws-athena-federation-sdk's ThrottlingInvoker
 * (see athena-lark-base's throttling/BaseExceptionFilter for the sibling exception-code list this
 * class's {@link #isThrottlingException} mirrors). The crawler makes far more sequential Lark API calls
 * per invocation than the connector does per query (list all bases, then all tables, then all fields per
 * table, all paginated), so it's at least as exposed to rate limiting - without any retry, a table or
 * field list hit by a transient 429-shaped error was simply dropped (caught by the per-table isolation
 * already in BaseLarkBaseCrawlerHandler, logged, and skipped) instead of ever being retried.
 * <p>
 * This is a small, standalone reimplementation of the same algorithm rather than a dependency on
 * aws-athena-federation-sdk's ThrottlingInvoker: this module (glue-lark-base-crawler) is a plain
 * Lambda + Glue + STS module with no existing dependency on the Athena federation SDK, and pulling in
 * that whole SDK (plus its transitive dependencies) just to reuse one retry-loop utility class would be
 * a disproportionate addition to this module's footprint.
 */
public final class ThrottlingRetry
{
    private static final Logger logger = LoggerFactory.getLogger(ThrottlingRetry.class);

    //10ms initial delay, matching athena-lark-base's ThrottlingInvoker defaults.
    private static final long DEFAULT_INITIAL_DELAY_MS = 10;
    //1s max delay between attempts.
    private static final long DEFAULT_MAX_DELAY_MS = 1_000;
    //Halve our call rate (double the delay) on each throttling event.
    private static final double DEFAULT_DECREASE_FACTOR = 0.5;
    //Ease the delay back down by 10ms after each success.
    private static final long DEFAULT_INCREASE_MS = 10;
    //Give up retrying a single call after 60s of being throttled - the crawler processes many
    //tables/fields per invocation and a Lambda has a hard overall timeout, so one persistently
    //throttled call must not be allowed to consume the whole budget.
    private static final long DEFAULT_TIMEOUT_MS = 60_000;

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double decreaseFactor;
    private final long increaseMs;
    private final long timeoutMs;
    private final AtomicLong delay = new AtomicLong(0);

    public ThrottlingRetry()
    {
        this(DEFAULT_INITIAL_DELAY_MS, DEFAULT_MAX_DELAY_MS, DEFAULT_DECREASE_FACTOR, DEFAULT_INCREASE_MS, DEFAULT_TIMEOUT_MS);
    }

    /**
     * @param timeoutMs Total time budget, across all retries, before giving up and rethrowing the last
     *                  throttling exception. A value &lt;= 0 means "don't retry at all" - the first
     *                  throttling exception is rethrown immediately - which is useful for tests that
     *                  need deterministic, fast failure rather than exercising real backoff delays.
     */
    public ThrottlingRetry(long initialDelayMs, long maxDelayMs, double decreaseFactor, long increaseMs, long timeoutMs)
    {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.decreaseFactor = decreaseFactor;
        this.increaseMs = increaseMs;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Invokes the given action, retrying with backoff if it throws an exception
     * {@link #isThrottlingException} recognizes as Lark rate limiting/congestion. Any other exception -
     * or a throttling exception once the retry time budget is exhausted - propagates to the caller
     * unchanged (not wrapped), so existing callers' own exception handling/messaging is unaffected.
     */
    public <T> T invoke(Callable<T> action) throws Exception
    {
        long startTime = System.currentTimeMillis();
        int attempt = 0;
        while (true) {
            attempt++;
            applySleep();
            try {
                T result = action.call();
                handleSuccess();
                return result;
            }
            catch (Exception ex) {
                if (!isThrottlingException(ex)) {
                    throw ex;
                }
                if (timeoutMs <= 0 || System.currentTimeMillis() - startTime >= timeoutMs) {
                    logger.warn("Giving up after {} attempt(s), still throttled: {}", attempt, ex.getMessage());
                    throw ex;
                }
                handleThrottle(ex);
            }
        }
    }

    /**
     * Matches the same Lark error codes/messages as athena-lark-base's BaseExceptionFilter treats as
     * throttling: direct rate limiting (1254290), write conflicts (1254291), request timeouts (1255040),
     * data-not-ready (1254607), and base-is-copying (1254036) - all congestion-shaped errors worth
     * backing off and retrying rather than failing immediately. This module's Lark API call sites throw
     * an IOException carrying Lark's numeric code and message text (e.g.
     * "Failed to retrieve tables for base: ..., Code: 1254290, Error: ..."), so a substring match on the
     * exception's message is sufficient without needing a typed exception hierarchy.
     */
    public static boolean isThrottlingException(Exception ex)
    {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }

        return message.contains("1254290") || message.contains("TooManyRequest")
                || message.contains("1254291") || message.contains("Write conflict")
                || message.contains("1255040") || message.contains("Request timed out")
                || message.contains("1254607") || message.contains("Data not ready")
                || message.contains("1254036") || message.contains("Base is copying");
    }

    private synchronized void handleThrottle(Exception ex)
    {
        long newDelay = (long) Math.ceil(delay.get() / decreaseFactor);
        if (newDelay == 0) {
            newDelay = initialDelayMs;
        }
        else if (newDelay > maxDelayMs) {
            newDelay = maxDelayMs;
        }
        logger.info("Throttling detected ({}), backing off to {} ms", ex.getMessage(), newDelay);
        delay.set(newDelay);
    }

    private synchronized void handleSuccess()
    {
        long newDelay = delay.get() - increaseMs;
        if (newDelay < 0) {
            newDelay = 0;
        }
        delay.set(newDelay);
    }

    /**
     * Clears any backed-off delay accumulated from throttling. This instance is held by a
     * {@code CommonLarkService} field, which - like the whole handler - is constructed once per Lambda
     * cold start and reused across every warm invocation, so without this a backoff ramped up by one
     * invocation's throttling would otherwise persist into the next, unrelated invocation on the same
     * warm container (decaying only {@link #DEFAULT_INCREASE_MS} per successful call). Callers should
     * invoke this once at the start of each Lambda invocation so retry state doesn't leak across them.
     */
    public void reset()
    {
        delay.set(0);
    }

    private void applySleep()
            throws InterruptedException
    {
        long currentDelay = delay.get();
        if (currentDelay > 0) {
            try {
                Thread.sleep(currentDelay);
            }
            catch (InterruptedException e) {
                // Restore the interrupt status Thread.sleep consumed before propagating, so a caller
                // further up the stack that checks Thread.currentThread().isInterrupted() still sees it.
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}
