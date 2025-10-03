package com.example.uaecarpalletreader;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import com.example.uaecarpalletreader.config.AnprProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@OpenAPIDefinition(
        info = @Info(
                title = "UAE Car Plate Reader API",
                version = "1.0",
                description = "REST API for extracting and normalizing UAE vehicle plate numbers from uploaded images.",
                contact = @Contact(name = "UAE Car Plate Reader")))
@SpringBootApplication
@EnableConfigurationProperties(AnprProperties.class)
public class UaeCarPalletReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(UaeCarPalletReaderApplication.class, args);
    }
}
