package com.codeagentoj.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class JudgeVerdictTest {
    @Test void coversAllVerdicts() {
        assertEquals("AC", JudgeVerdict.classify(false,false,0," 42\n","42"));
        assertEquals("WA", JudgeVerdict.classify(false,false,0,"41","42"));
        assertEquals("RE", JudgeVerdict.classify(false,false,1,"","42"));
        assertEquals("TLE", JudgeVerdict.classify(true,false,0,"","42"));
        assertEquals("MLE", JudgeVerdict.classify(false,true,0,"","42"));
    }
}
