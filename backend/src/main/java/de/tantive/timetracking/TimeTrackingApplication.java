package de.tantive.timetracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class TimeTrackingApplication {
    public static void main(String[] args) { SpringApplication.run(TimeTrackingApplication.class, args); }
}
