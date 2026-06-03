package com.niki;

import com.niki.config.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NikiBotApplication {

    public static void main(String[] args) {
        EnvLoader.loadDotEnvIfPresent();
        SpringApplication.run(NikiBotApplication.class, args);
    }
}
