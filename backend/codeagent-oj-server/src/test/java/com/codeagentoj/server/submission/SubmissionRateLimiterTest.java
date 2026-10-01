package com.codeagentoj.server.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 限流规则回归：短时连点按分钟挡、长时高频按小时挡，且只影响触发者本人。 */
class SubmissionRateLimiterTest {
    private static final long T0 = 1_700_000_000_000L;

    @Test void allowsFirstSubmission() {
        assertEquals(SubmissionRateLimiter.Decision.ALLOW, new SubmissionRateLimiter(3, 10).check(7L, T0));
    }

    @Test void blocksBurstWithinAMinute() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(3, 10);
        for (int index = 0; index < 3; index++) {
            assertEquals(SubmissionRateLimiter.Decision.ALLOW, limiter.check(7L, T0 + index * 1000L));
            limiter.record(7L, T0 + index * 1000L);
        }
        assertEquals(SubmissionRateLimiter.Decision.PER_MINUTE, limiter.check(7L, T0 + 4_000L));
    }

    @Test void recoversAfterAMinute() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(2, 10);
        limiter.record(7L, T0);
        limiter.record(7L, T0 + 1_000L);
        assertEquals(SubmissionRateLimiter.Decision.PER_MINUTE, limiter.check(7L, T0 + 2_000L));
        assertEquals(SubmissionRateLimiter.Decision.ALLOW, limiter.check(7L, T0 + 61_000L));
    }

    @Test void hourlyCapStopsSustainedFlooding() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(1, 3);
        for (int index = 0; index < 3; index++) {
            assertEquals(SubmissionRateLimiter.Decision.ALLOW, limiter.check(7L, T0 + index * 120_000L));
            limiter.record(7L, T0 + index * 120_000L);
        }
        assertEquals(SubmissionRateLimiter.Decision.PER_HOUR, limiter.check(7L, T0 + 400_000L));
        assertEquals(SubmissionRateLimiter.Decision.ALLOW, limiter.check(7L, T0 + 3_700_000L));
    }

    @Test void limitsArePerUser() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(1, 1);
        limiter.record(7L, T0);
        assertEquals(SubmissionRateLimiter.Decision.PER_HOUR, limiter.check(7L, T0 + 1_000L));
        assertEquals(SubmissionRateLimiter.Decision.ALLOW, limiter.check(8L, T0 + 1_000L));
    }
}
