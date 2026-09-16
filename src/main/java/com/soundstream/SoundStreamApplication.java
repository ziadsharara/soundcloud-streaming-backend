package com.soundstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SoundStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoundStreamApplication.class, args);
    }
}
