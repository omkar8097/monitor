package com.example.monitor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/v1/greetings")
public class GreetingController {

    private static final String[] RANDOM_GREETINGS = {
        "Hello from Spring Boot!",
        "Welcome to the developer playground!",
        "Greetings, human! How can I assist you today?",
        "Bonjour! Hope you have a wonderful coding session.",
        "Hola! Coding in Java 21 feels fantastic!",
        "Ciao! Keep building amazing things.",
        "Namaste! The API server is fully operational."
    };

    private final Random random = new Random();

    @GetMapping("/hello")
    public Map<String, String> sayHello(@RequestParam(value = "name", defaultValue = "Developer") String name) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello, " + name + "!");
        response.put("status", "success");
        return response;
    }

    @GetMapping("/random")
    public Map<String, String> getRandomGreeting() {
        int index = random.nextInt(RANDOM_GREETINGS.length);
        Map<String, String> response = new HashMap<>();
        response.put("greeting", RANDOM_GREETINGS[index]);
        response.put("status", "success");
        return response;
    }
}
