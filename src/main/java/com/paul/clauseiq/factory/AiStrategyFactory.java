package com.paul.clauseiq.factory;

import com.paul.clauseiq.strategy.AIStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiStrategyFactory {

    private final Map<String, AIStrategy> strategies;

    public AIStrategy get(String provider) {

        AIStrategy strategy = strategies.get(provider.toLowerCase());

        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported AI Provider : " + provider);
        }

        return strategy;
    }
}
