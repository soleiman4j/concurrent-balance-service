package io.github.soleiman4j;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.soleiman4j.BalanceException.Reason.*;

@Service
public final class InMemoryBalanceService implements BalanceService {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransactionEntry> transactions = new ConcurrentHashMap<>();

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
        requireId(accountId, "accountId");
        requirePositiveAmount(amount);
        once(transactionId, new Request(Operation.CREDIT, accountId, null, amount), () -> {
            Account account = findAccount(accountId);
            account.lock.lock();
            try {
                account.balance = safeAdd(account.balance, amount);
            } finally {
                account.lock.unlock();
            }
        });
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        requireId(accountId, "accountId");
        requirePositiveAmount(amount);
        once(transactionId, new Request(Operation.DEBIT, accountId, null, amount), () -> {
            Account account = findAccount(accountId);
            account.lock.lock();
            try {
                requireFunds(account, amount);
                account.balance -= amount;
            } finally {
                account.lock.unlock();
            }
        });
    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId,
                         long amount, String transactionId) {
        requireId(sourceAccountId, "sourceAccountId");
        requireId(destinationAccountId, "destinationAccountId");
        requirePositiveAmount(amount);
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new BalanceException(INVALID_INPUT, "Cannot transfer to the same account");
        }

        once(transactionId,
                new Request(Operation.TRANSFER, sourceAccountId, destinationAccountId, amount),
                () -> {
                    Account source = findAccount(sourceAccountId);
                    Account destination = findAccount(destinationAccountId);

                    boolean sourceFirst = sourceAccountId.compareTo(destinationAccountId) < 0;
                    Account first = sourceFirst ? source : destination;
                    Account second = sourceFirst ? destination : source;

                    first.lock.lock();
                    try {
                        second.lock.lock();
                        try {
                            requireFunds(source, amount);
                            long updatedDestination = safeAdd(destination.balance, amount);
                            source.balance -= amount;
                            destination.balance = updatedDestination;
                        } finally {
                            second.lock.unlock();
                        }
                    } finally {
                        first.lock.unlock();
                    }
                });
    }

    @Override
    public long getBalance(String accountId) {
        requireId(accountId, "accountId");
        Account account = findAccount(accountId);
        account.lock.lock();
        try {
            return account.balance;
        } finally {
            account.lock.unlock();
        }
    }

    private static void requireId(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new BalanceException(INVALID_INPUT, name + " must not be blank");
        }
    }

    private static final class Account {
        private final ReentrantLock lock = new ReentrantLock();
        private long balance;

        private Account(long openingBalance) {
            this.balance = openingBalance;
        }
    }

    private static void requirePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new BalanceException(INVALID_INPUT, "amount must be > 0");
        }
    }

    private void once(String transactionId, Request request, Runnable operation) {
        requireId(transactionId, "transactionId");
        TransactionEntry candidate = new TransactionEntry(request);
        TransactionEntry previous = transactions.putIfAbsent(transactionId, candidate);
        TransactionEntry entry = previous == null ? candidate : previous;

        if (!entry.request.equals(request)) {
            throw new BalanceException(TRANSACTION_ID_CONFLICT,
                    "transactionId already used for another operation: " + transactionId);
        }

        synchronized (entry) {
            if (entry.completed) {
                rethrow(entry.failure);
                return;
            }
            try {
                operation.run();
            } catch (RuntimeException | Error failure) {
                entry.failure = failure;
                throw failure;
            } finally {
                entry.completed = true;
            }
        }
    }

    private record Request(Operation type, String source, String destination, long amount) { }
    private enum Operation { CREDIT, DEBIT, TRANSFER }

    private static final class TransactionEntry {
        private final Request request;
        private boolean completed;
        private Throwable failure;

        private TransactionEntry(Request request) {
            this.request = request;
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private Account findAccount(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new BalanceException(UNKNOWN_ACCOUNT, "Unknown account: " + accountId);
        }
        return account;
    }

    private static void requireFunds(Account account, long amount) {
        if (account.balance < amount) {
            throw new BalanceException(INSUFFICIENT_FUNDS, "Insufficient funds");
        }
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw new BalanceException(BALANCE_OVERFLOW, "Balance would exceed Long.MAX_VALUE");
        }
    }
}