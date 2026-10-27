package com.example.knavi.intelligence.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiProperties.class, EmbeddingProperties.class})
class IntelligenceConfig {}
