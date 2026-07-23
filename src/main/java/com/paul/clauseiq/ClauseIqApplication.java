package com.paul.clauseiq;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

@SpringBootApplication
public class ClauseIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClauseIqApplication.class, args);
    }

    @Bean
    ApplicationRunner aiBeans(ApplicationContext context) {
        return args -> {
            System.out.println("=== Chat Models ===");
            Arrays.stream(context.getBeanNamesForType(org.springframework.ai.chat.model.ChatModel.class))
                    .forEach(System.out::println);

            System.out.println("=== Embedding Models ===");
            Arrays.stream(context.getBeanNamesForType(org.springframework.ai.embedding.EmbeddingModel.class))
                    .forEach(System.out::println);
        };
    }
}
