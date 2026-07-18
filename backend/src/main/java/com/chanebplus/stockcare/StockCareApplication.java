package com.chanebplus.stockcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StockCare MVP backend entry point.
 *
 * <p>Predictive Demand and Priority-Aware Distribution network for pharmacies (team Chaneb+).
 * This modular monolith is structured so individual modules can later be extracted as services.
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableScheduling
public class StockCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockCareApplication.class, args);
    }
}
