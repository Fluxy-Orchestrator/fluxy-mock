package org.fluxy.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class FluxymockApplication {

    static void main(String[] args) {
        SpringApplication.run(FluxymockApplication.class, args);
    }
}

