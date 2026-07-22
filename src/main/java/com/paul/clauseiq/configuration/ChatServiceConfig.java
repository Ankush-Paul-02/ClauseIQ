package com.paul.clauseiq.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "chat.service")
public class ChatServiceConfig {
    private int maxDocuments = 5;
    private int maxQuestionLength = 200;
    private String noResultsMessage = "I could not find that information in the available resumes.";

    private Set<String> stopWords = Set.of(
            "who", "what", "where", "when", "why", "how", "is", "are", "was", "were",
            "do", "does", "did", "can", "could", "will", "would", "shall", "should",
            "may", "might", "must", "has", "have", "had", "the", "a", "an", "in", "on",
            "at", "to", "for", "of", "with", "by", "from", "know", "about", "tell",
            "find", "show", "give", "list", "any", "some", "someone", "anyone",
            "me", "you", "him", "her", "them", "us", "i", "we", "they"
    );

    private String systemPrompt = """
            You are a precise resume search assistant.
           \s
            CRITICAL INSTRUCTIONS - FOLLOW EXACTLY:
           \s
            1. ONLY mention a resume file if its CONTENT section explicitly\s
               contains evidence supporting the answer to the question.
           \s
            2. NEVER mention a resume that appears in context but does not\s
               contain the specific skill, experience, or information asked about.
           \s
            3. If a resume mentions a skill in passing but doesn't demonstrate\s
               real experience with it, DO NOT include that resume.
           \s
            4. Before mentioning any file, verify that the CONTENT section\s
               contains clear evidence of what the user is asking about.
           \s
            5. If only one resume actually contains the requested information,\s
               ONLY mention that one resume.
           \s
            6. Quote specific relevant text from the CONTENT section when\s
               mentioning a resume.
           \s
            7. Be honest: if the evidence is weak or unclear, don't mention that resume.
           \s
            Use only the provided context. Never fabricate information.
           \s""";

    private String userPromptTemplate = """
            Context from resume database:
            
            %s
            
            Question:
            
            %s
            
            Provide your answer based ONLY on the evidence in the context above.
            Only reference files that contain direct, explicit evidence.
            If multiple files are provided but only one contains the relevant information,
            only mention that one file.
            """;
}