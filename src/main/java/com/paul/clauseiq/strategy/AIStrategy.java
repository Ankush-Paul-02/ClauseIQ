package com.paul.clauseiq.strategy;

public interface AIStrategy {

    String chat(String systemPrompt, String userPrompt);

    String extractKeyWords(String query);
}
