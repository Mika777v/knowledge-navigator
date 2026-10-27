package com.example.knavi.navigation.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({NavigationWeights.class, VectorSearchProperties.class})
class NavigationConfig {}
