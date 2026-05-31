package com.niki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.starter.TelegramBotStarterConfiguration;

@SpringBootApplication(exclude = TelegramBotStarterConfiguration.class)
@EnableScheduling
public class NikiBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NikiBotApplication.class, args);
    }
}
