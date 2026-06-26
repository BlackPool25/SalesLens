package com.shreyas.saleslens.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Value("${langchain4j.ollama.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String modelName;

    @Bean
    @Profile("!test")
    public ChatLanguageModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .temperature(0.0)
                .build();
    }

    @Bean
    @Profile("test")
    public ChatLanguageModel mockOllamaChatModel() {
        return new ChatLanguageModel() {
            @Override
            public String generate(String userMessage) {
                return "{\"canonicalEntity\": \"orders\", \"canonicalField\": \"total_amount\", \"confidence\": 0.95}";
            }

            @Override
            public dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> generate(java.util.List<dev.langchain4j.data.message.ChatMessage> messages) {
                return dev.langchain4j.model.output.Response.from(dev.langchain4j.data.message.AiMessage.from("{\"canonicalEntity\": \"orders\", \"canonicalField\": \"total_amount\", \"confidence\": 0.95}"));
            }
        };
    }
}
