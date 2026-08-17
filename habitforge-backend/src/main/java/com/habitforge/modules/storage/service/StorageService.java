package com.habitforge.modules.storage.service;

import java.io.InputStream;

/**
 * 对象存储服务（MinIO）：MySQL 只存 objectKey，字节落 MinIO
 */
public interface StorageService {

    /** 上传对象，失败抛 BusinessException(STORAGE_ERROR) */
    void putObject(String objectKey, InputStream stream, long size, String contentType);

    /** 删除对象，失败仅记录日志（best-effort，不阻断业务） */
    void deleteObjectQuietly(String objectKey);
}
