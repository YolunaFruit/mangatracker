package com.joshua.mangatracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class MangaTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MangaTrackerApplication.class, args);
    }
    // to use, enter through terminal and find tracker-ui. Then type npm start
}
