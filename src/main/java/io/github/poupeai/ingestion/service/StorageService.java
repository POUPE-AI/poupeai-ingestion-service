package io.github.poupeai.ingestion.service;

import java.io.InputStream;

public interface StorageService {
    InputStream downloadFile(String fileKey);
}
