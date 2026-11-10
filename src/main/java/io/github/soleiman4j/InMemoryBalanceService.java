package io.github.soleiman4j;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.soleiman4j.BalanceException.Reason.*;

@Service
public final class InMemoryBalanceService implements BalanceService {

    public void createAccount(String accountId, long openingBalance) {
        requireId(accountId, "accountId");
        if (openingBalance < 0) {
            throw new BalanceException(INVALID_INPUT, "openingBalance must be >= 0");
        }
        if (accounts.putIfAbsent(accountId, new Account(openingBalance)) != null) {
            throw new BalanceException(ACCOUNT_ALREADY_EXISTS, "Account already exists: " + accountId);
        }
    }

    @Override
    public void credit(String accountId, long amount, String transactionId) {

    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {

    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {

    }

    @Override
    public long getBalance(String accountId) {
        return 0;
    }

    private static void requireId(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new BalanceException(INVALID_INPUT, name + " must not be blank");
        }
    }

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    private static final class Account {
        private final ReentrantLock lock = new ReentrantLock();
        private long balance;

        private Account(long openingBalance) {
            this.balance = openingBalance;
        }
    }
}