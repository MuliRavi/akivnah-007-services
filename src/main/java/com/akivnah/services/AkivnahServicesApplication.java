package com.akivnah.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AkivnahServicesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AkivnahServicesApplication.class, args);
    }
}
