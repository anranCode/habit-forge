package com.habitforge.modules.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 连接配置（endpoint/凭据由 profile 或环境变量提供）
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** 如 http://localhost:9000 或 http://minio-host:9000 */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    /** 存储桶名称 */
    private String bucket;
}
