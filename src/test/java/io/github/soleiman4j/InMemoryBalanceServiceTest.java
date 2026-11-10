package io.github.soleiman4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.soleiman4j.BalanceException.Reason.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryBalanceServiceTest {
    private InMemoryBalanceService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryBalanceService();
        service.createAccount("A", 1_000);
        service.createAccount("B", 500);
    }

    @Test
    void duplicateAccountCreationIsRejected() {
        assertEquals(ACCOUNT_ALREADY_EXISTS, assertThrows(BalanceException.class,
                () -> service.createAccount("A", 123)).reason());
    }

    @Test
    void repeatedTransfersDoNotMoveMoneyAgain() {
        service.transfer("A", "B", 300, "tx-3");
        service.transfer("A", "B", 300, "tx-3");
        assertEquals(700, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }

    @Test
    void transferOverflowDoesNotPartiallyDebitSource() {
        service.createAccount("MAX", Long.MAX_VALUE);
        BalanceException error = assertThrows(BalanceException.class,
                () -> service.transfer("A", "MAX", 1, "overflow-transfer"));
        assertEquals(BALANCE_OVERFLOW, error.reason());
        assertEquals(1_000, service.getBalance("A"));
        assertEquals(Long.MAX_VALUE, service.getBalance("MAX"));
    }
}
