package io.github.soleiman4j;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BalanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BalanceApplication.class, args);
    }

    @Bean
    ApplicationRunner demo(InMemoryBalanceService service) {
        return args -> {
            service.createAccount("A", 1_000);
            service.createAccount("B", 500);
            service.transfer("A", "B", 300, "DEMO-TX-1");
            service.transfer("A", "B", 300, "DEMO-TX-1");
            System.out.println("Balance A = " + service.getBalance("A"));
            System.out.println("Balance B = " + service.getBalance("B"));
            System.out.println("The second transfer request was ignored (idempotent retry).");
        };
    }
}