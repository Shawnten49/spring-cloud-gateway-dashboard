package com.example.gatewaydemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GatewayDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayDemoApplication.class, args);
    }
}
