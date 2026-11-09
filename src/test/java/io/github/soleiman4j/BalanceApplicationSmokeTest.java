package io.github.soleiman4j;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BalanceApplicationSmokeTest {
    @Autowired
    private BalanceService service;

    @Test
    void springContextStartsAndDemoRuns() {
        assertEquals(700, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }
}