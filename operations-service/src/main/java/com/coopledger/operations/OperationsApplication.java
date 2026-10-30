package com.coopledger.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.coopledger.operations", "com.coopledger.shared"})
public class OperationsApplication {
    public static void main(String[] args) { SpringApplication.run(OperationsApplication.class, args); }
}
