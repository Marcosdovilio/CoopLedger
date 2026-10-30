package com.coopledger.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.coopledger.core", "com.coopledger.shared"})
public class CoreApplication {
    public static void main(String[] args) { SpringApplication.run(CoreApplication.class, args); }
}
