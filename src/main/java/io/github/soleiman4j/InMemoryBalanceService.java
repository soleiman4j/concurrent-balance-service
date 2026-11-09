package io.github.soleiman4j;

import org.springframework.stereotype.Service;

@Service
public final class InMemoryBalanceService implements BalanceService {
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
}