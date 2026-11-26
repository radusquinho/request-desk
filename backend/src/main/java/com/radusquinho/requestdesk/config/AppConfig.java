package com.radusquinho.requestdesk.config;

import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConfigurationPropertiesScan("com.radusquinho.requestdesk")
public class AppConfig {

    /** Injected everywhere time matters, so tests can freeze or move the clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
