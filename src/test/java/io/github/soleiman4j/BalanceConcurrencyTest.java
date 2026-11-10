package io.github.soleiman4j;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.github.soleiman4j.BalanceException.Reason.*;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.junit.jupiter.api.Assertions.*;

class BalanceConcurrencyTest {

    @Test
    void twoSimultaneousDebitsAllowOnlyOneSuccess() throws Exception {
        var service = new InMemoryBalanceService();
        service.createAccount("A", 1_000);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = newVirtualThreadPerTaskExecutor()) { // Virtual threads: Java 21.
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final String transactionId = "debit-" + i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        service.debit("A", 700, transactionId);
                        return true;
                    } catch (BalanceException error) {
                        if (error.reason() != INSUFFICIENT_FUNDS) throw error;
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            long successes = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(300, service.getBalance("A"));
        }
    }

    @Test
    void thousandSimultaneousCreditsProduceExactBalance() throws Exception {
        var service = new InMemoryBalanceService();
        service.createAccount("A", 0);
        var start = new CountDownLatch(1);
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                final String id = "credit-" + i;
                tasks.add(executor.submit(() -> {
                    start.await();
                    service.credit("A", 1, id);
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1_000, service.getBalance("A"));
    }

    @Test
    void sameTransactionIdFromManyThreadsAppliesOnlyOnce() throws Exception {
        var service = new InMemoryBalanceService();
        service.createAccount("A", 0);
        var start = new CountDownLatch(1);
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 150; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    service.credit("A", 50, "same-id");
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(50, service.getBalance("A"));
    }

    @Test
    void independentAccountsCanBeUpdatedConcurrently() throws Exception {
        var service = new InMemoryBalanceService();
        service.createAccount("A", 0);
        service.createAccount("B", 0);
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                final String id = "tx-" + i;
                tasks.add(executor.submit(() -> {
                    service.credit("A", 1, "A-" + id);
                    return null;
                }));
                tasks.add(executor.submit(() -> {
                    service.credit("B", 2, "B-" + id);
                    return null;
                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(500, service.getBalance("A"));
        assertEquals(1_000, service.getBalance("B"));
    }

    @Test
    void transfersInOppositeDirectionsDoNotDeadlockOrLoseFunds() throws Exception {
        var service = new InMemoryBalanceService();
        service.createAccount("A", 10_000);
        service.createAccount("B", 10_000);
        var start = new CountDownLatch(1);
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                final String id = "both-" + i;
                tasks.add(executor.submit(() -> {
                    start.await();
                    service.transfer("A", "B", 1, id + "-A-B");
                    return null;
                }));
                tasks.add(executor.submit(() -> {
                    start.await();
                    service.transfer("B", "A", 1, id + "-B-A");
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(10_000, service.getBalance("A"));
        assertEquals(10_000, service.getBalance("B"));
    }
}
