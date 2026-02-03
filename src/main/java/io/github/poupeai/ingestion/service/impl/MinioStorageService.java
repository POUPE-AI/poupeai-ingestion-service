package io.github.poupeai.ingestion.service.impl;

import io.github.poupeai.ingestion.domain.exception.StorageException;
import io.github.poupeai.ingestion.service.StorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {
    private final MinioClient minioClient;

    @Value("${app.minio.bucket}")
    private String bucketName;

    @Override
    public InputStream downloadFile(String fileKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileKey)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Falha ao baixar arquivo do storage: " + fileKey, e);
        }
    }
}
