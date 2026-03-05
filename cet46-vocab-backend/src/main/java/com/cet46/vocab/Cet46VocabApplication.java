package com.cet46.vocab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Cet46VocabApplication {

    public static void main(String[] args) {
        SpringApplication.run(Cet46VocabApplication.class, args);
    }
}
