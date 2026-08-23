package org.grandparents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.grandparents")
@EnableScheduling
@EnableAsync
public class GrandparentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrandparentsApplication.class, args);
    }

}
