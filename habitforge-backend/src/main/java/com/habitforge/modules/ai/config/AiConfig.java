package com.habitforge.modules.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 业务护栏参数注册（仿 MinioConfig 对 MinioProperties 的两件套注册方式）
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
