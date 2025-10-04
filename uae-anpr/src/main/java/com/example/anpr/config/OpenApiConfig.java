package com.example.anpr.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI plateRecognitionApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UAE License Plate Recognition API")
                        .description("REST API that extracts the emirate, letter and number from UAE license plate images.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("UAE OCR ANPR")
                                .url("https://github.com/")));
    }
}
