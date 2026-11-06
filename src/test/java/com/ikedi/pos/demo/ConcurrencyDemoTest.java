package com.ikedi.pos.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConcurrencyDemoTest {

    @Test
    void shouldExecuteConcurrencyScenariosSuccessfully() {
        boolean passed = ConcurrencyDemo.runAllScenarios(false);
        assertTrue(passed, "All concurrency and deadlock scenarios must pass");
    }
}

