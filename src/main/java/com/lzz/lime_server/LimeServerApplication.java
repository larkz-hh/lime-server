package com.lzz.lime_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LimeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LimeServerApplication.class, args);
    }

}
