package com.paul.clauseiq.configuration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    EmbeddingModel embeddingModel(
            @Qualifier("ollamaEmbeddingModel")
            EmbeddingModel embeddingModel
    ) {
        return embeddingModel;
    }
}