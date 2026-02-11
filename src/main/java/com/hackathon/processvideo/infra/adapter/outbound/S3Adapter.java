package com.hackathon.processvideo.infra.adapter.outbound;

import com.hackathon.processvideo.domain.exception.FileNotExistException;
import com.hackathon.processvideo.domain.port.out.FileServicePort;
import com.hackathon.processvideo.domain.port.out.LoggerPort;
import com.hackathon.processvideo.domain.exception.VideoProcessingException;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
@AllArgsConstructor
public class S3Adapter implements FileServicePort {

    private S3Template s3Template;
    private LoggerPort loggerPort;

    @Override
    public InputStream getFile(String bucketName, String key_name){
        try{
            loggerPort.info("[S3Adapter][getFile] Downloading file from S3, bucket={}, key={}", bucketName, key_name);
            return s3Template.download(bucketName, key_name).getInputStream();
        }catch(IOException exception){
            loggerPort.error("[S3Adapter][getFile] File not found in S3, bucket={}, key={}", bucketName, key_name);
            throw new FileNotExistException("Arquivo não encontrado: " + key_name);
        }
    }

    @Override
    public void deleteFile(String bucketName, String key) {
        try {
            loggerPort.debug("[S3Adapter][deleteFile] Deleting file from S3, bucket={}, key={}", bucketName, key);
            s3Template.deleteObject(bucketName, key);
            loggerPort.info("[S3Adapter][deleteFile] File deleted successfully, bucket={}, key={}", bucketName, key);
        } catch (Exception e) {
            loggerPort.warn("[S3Adapter][deleteFile] Error deleting file from S3, bucket={}, key={}, error={}", bucketName, key, e.getMessage());
        }
    }

    @Override
    public boolean uploadFile(String bucketName, String key, InputStream file) {
        try (file) {
            loggerPort.info("[S3Adapter][uploadFile] Starting upload to S3, bucket={}, key={}", bucketName, key);
            S3Resource upload = s3Template.upload(bucketName, key, file);
            boolean exists = upload.exists();
            if (exists) {
                loggerPort.info("[S3Adapter][uploadFile] Upload completed successfully, bucket={}, key={}", bucketName, key);
            } else {
                loggerPort.error("[S3Adapter][uploadFile] Upload verification failed, bucket={}, key={}", bucketName, key);
            }
            return exists;
        } catch (IOException e) {
            loggerPort.error("[S3Adapter][uploadFile] Error during upload to S3, bucket={}, key={}, error={}", bucketName, key, e.getMessage());
            return false;
        }
    }

    @Override
    public Long getSize(String bucketName, String key) {
        try {
            loggerPort.debug("[S3Adapter][getSize] Retrieving file size, bucket={}, key={}", bucketName, key);
            var s3Resource = s3Template.download(bucketName, key);

            if (!s3Resource.exists()) {
                loggerPort.error("[S3Adapter][getSize] File not found in S3, bucket={}, key={}", bucketName, key);
                throw new VideoProcessingException("Arquivo não encontrado no S3: " + key, null);
            }

            long size = s3Resource.contentLength();
            loggerPort.info("[S3Adapter][getSize] File size retrieved, bucket={}, key={}, size={}bytes", bucketName, key, size);
            return size;
        } catch (Exception e) {
            loggerPort.error("[S3Adapter][getSize] Error retrieving file size, bucket={}, key={}, error={}", bucketName, key, e.getMessage());
            throw new VideoProcessingException("Erro ao obter tamanho do arquivo: " + key, e);
        }
    }
}
