package com.influxdb.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("🚀 Pokretanje InfluxDB Mikroservisa...");
        SpringApplication.run(Application.class, args);
        logger.info("✅ Aplikacija je pokrenuta!");
    }

    @Bean
    public ObjectMapper objectMapper() {
        // Registruj JSR-310 modul da bi java.time.Instant mogao da se serijalizuje;
        // bez ovoga MVC odgovori sa timestamp poljem padaju na 500.
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
