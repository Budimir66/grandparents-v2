package org.example.grandparents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.grandparents")
public class GrandparentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrandparentsApplication.class, args);
    }

}
