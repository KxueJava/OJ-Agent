package com.codeagentoj.server.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Supervisor 规划的回归：确定性规则也必须保证"不浪费专家、不遗漏必须的专家"。 */
class AgentOrchestratorTest {
    @Test void compileErrorOnlyNeedsTheDebugger() {
        AgentOrchestrator.Plan plan = AgentOrchestrator.plan("CE", true);
        assertEquals(List.of(AgentOrchestrator.Specialist.DEBUGGER), plan.specialists());
    }

    @Test void runtimeFailureRunsDebuggerAndReviewerInParallel() {
        AgentOrchestrator.Plan plan = AgentOrchestrator.plan("WA", true);
        assertEquals(2, plan.specialists().size());
        assertTrue(plan.specialists().contains(AgentOrchestrator.Specialist.DEBUGGER));
        assertTrue(plan.specialists().contains(AgentOrchestrator.Specialist.REVIEWER));
        assertTrue(plan.specialists().size() <= 3, "每轮业务 Agent 不超过 3 个");
    }

    @Test void missingSourceCodeDispatchesNobody() {
        assertTrue(AgentOrchestrator.plan("WA", false).specialists().isEmpty());
    }
}
