package com.habitforge.modules.storage.service.impl;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.storage.config.MinioProperties;
import com.habitforge.modules.storage.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 对象存储实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /**
     * 启动时确保桶存在并开放公开读（图片经 nginx 代理给前端，桶本身单入口收敛）
     * MinIO 不可用时不阻断应用启动，上传时才会报错
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initBucket() {
        try {
            String bucket = properties.getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 桶已创建: {}", bucket);
            }
            // public-read policy：仅放行 GetObject
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("MinIO 桶就绪: {} (public-read)", bucket);
        } catch (Exception e) {
            log.error("MinIO 初始化失败（不阻断启动，上传时将报错）: {}", e.getMessage());
        }
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败 objectKey={}", objectKey, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void deleteObjectQuietly(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 删除失败（孤儿对象，可后续清理）objectKey={}: {}", objectKey, e.getMessage());
        }
    }
}
