package com.codeagentoj.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 失败归类回归：用户代码问题与判题基础设施问题必须分开，否则界面会误导使用者去改代码。 */
class JudgeFailureTest {

    @Test void acceptedHasNoFailureKind() {
        JudgeWorker.Failure failure = JudgeWorker.classify("AC", false, "PUBLIC");
        assertNull(failure.kind());
        assertEquals("全部测试点通过", failure.message());
    }

    @Test void infrastructureFailureIsNotBlamedOnUserCode() {
        JudgeWorker.Failure failure = JudgeWorker.classify("RE", true, "PUBLIC");
        assertEquals("INFRA", failure.kind());
        assertEquals("判题基础设施异常（沙盒不可用），与你的代码无关", failure.message());
    }

    @Test void userVerdictsCarryUserKind() {
        assertEquals("USER", JudgeWorker.classify("WA", false, "PUBLIC").kind());
        assertEquals("公开样例输出不匹配", JudgeWorker.classify("WA", false, "PUBLIC").message());
        assertEquals("隐藏测试点未通过", JudgeWorker.classify("WA", false, "HIDDEN").message());
        assertEquals("运行超时（超过时限）", JudgeWorker.classify("TLE", false, "PUBLIC").message());
        assertEquals("内存超限", JudgeWorker.classify("MLE", false, "PUBLIC").message());
        assertEquals("运行期错误（程序非正常退出）", JudgeWorker.classify("RE", false, "PUBLIC").message());
        assertEquals("编译错误", JudgeWorker.classify("CE", false, null).message());
    }
}
