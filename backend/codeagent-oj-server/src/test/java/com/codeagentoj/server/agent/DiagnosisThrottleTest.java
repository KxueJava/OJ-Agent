package com.codeagentoj.server.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 节流规则回归：冷却是短冷却、额度才是成本闸，且额度按滚动小时恢复。 */
class DiagnosisThrottleTest {
    private static final long T0 = 1_700_000_000_000L;

    @Test void allowsFirstDiagnosis() {
        DiagnosisThrottle throttle = new DiagnosisThrottle(20, 5);
        assertEquals(DiagnosisThrottle.Decision.ALLOW, throttle.check(7L, 2118L, T0));
    }

    @Test void cooldownBlocksRapidResubmissionButRecoversQuickly() {
        DiagnosisThrottle throttle = new DiagnosisThrottle(20, 5);
        throttle.record(7L, 2118L, T0);
        assertEquals(DiagnosisThrottle.Decision.COOLDOWN, throttle.check(7L, 2118L, T0 + 5_000));
        assertEquals(DiagnosisThrottle.Decision.ALLOW, throttle.check(7L, 2118L, T0 + 21_000));
    }

    @Test void cooldownIsPerProblemNotGlobal() {
        DiagnosisThrottle throttle = new DiagnosisThrottle(20, 5);
        throttle.record(7L, 2118L, T0);
        assertEquals(DiagnosisThrottle.Decision.ALLOW, throttle.check(7L, 2101L, T0 + 1_000));
    }

    @Test void hourlyCapStopsRunawayCostThenRecovers() {
        DiagnosisThrottle throttle = new DiagnosisThrottle(0, 3);
        for (int index = 0; index < 3; index++) {
            assertEquals(DiagnosisThrottle.Decision.ALLOW, throttle.check(7L, 2118L, T0 + index * 60_000L));
            throttle.record(7L, 2118L, T0 + index * 60_000L);
        }
        assertEquals(DiagnosisThrottle.Decision.HOURLY_CAP, throttle.check(7L, 2118L, T0 + 240_000L));
        assertEquals(DiagnosisThrottle.Decision.ALLOW, throttle.check(7L, 2118L, T0 + 3_700_000L));
    }

    @Test void capsArePerUser() {
        DiagnosisThrottle throttle = new DiagnosisThrottle(0, 1);
        throttle.record(7L, 2118L, T0);
        assertEquals(DiagnosisThrottle.Decision.HOURLY_CAP, throttle.check(7L, 2101L, T0 + 1_000));
        assertEquals(DiagnosisThrottle.Decision.ALLOW, throttle.check(8L, 2101L, T0 + 1_000));
    }
}
