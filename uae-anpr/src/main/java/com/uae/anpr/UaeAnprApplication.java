package com.uae.anpr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UaeAnprApplication {

    public static void main(String[] args) {
        SpringApplication.run(UaeAnprApplication.class, args);
    }
}
