package com.paul.clauseiq.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class BeanPrinter {

    public BeanPrinter(ApplicationContext context) {

        Arrays.stream(context.getBeanDefinitionNames())
                .sorted()
                .filter(name ->
                        name.toLowerCase().contains("chat")
                                || name.toLowerCase().contains("genai")
                                || name.toLowerCase().contains("ollama"))
                .forEach(System.out::println);
    }
}