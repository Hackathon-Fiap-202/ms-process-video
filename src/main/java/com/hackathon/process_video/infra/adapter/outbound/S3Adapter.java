package com.hackathon.process_video.infra.adapter.outbound;

import com.hackathon.process_video.domain.exception.FileNotExistException;
import com.hackathon.process_video.domain.port.out.FileServicePort;
import com.hackathon.process_video.domain.port.out.LoggerPort;
import com.hackathon.process_video.domain.exception.VideoProcessingException;
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
            loggerPort.info("Baixando arquivo do S3: {}/{}", bucketName, key_name);
            return s3Template.download(bucketName, key_name).getInputStream();
        }catch(IOException exception){
            loggerPort.error("Arquivo não encontrado no S3: {}/{}", bucketName, key_name);
            throw new FileNotExistException("Arquivo não encontrado: " + key_name);
        }
    }

    @Override
    public void deleteFile(String bucketName, String key) {
        try {
            s3Template.deleteObject(bucketName, key);
            loggerPort.info("Arquivo deletado do S3: {}/{}", bucketName, key);
        } catch (Exception e) {
            loggerPort.warn("Erro ao deletar arquivo do S3: {}/{} - {}", bucketName, key, e.getMessage());
        }
    }

    @Override
    public boolean uploadFile(String bucketName, String key, InputStream file) {
        try (file) {
            loggerPort.info("Iniciando upload para S3: {}/{}", bucketName, key);
            S3Resource upload = s3Template.upload(bucketName, key, file);
            boolean exists = upload.exists();
            if (exists) {
                loggerPort.info("Upload concluído com sucesso: {}/{}", bucketName, key);
            }
            return exists;
        } catch (IOException e) {
            loggerPort.error("Erro durante upload para S3: {}/{} - {}", bucketName, key, e.getMessage());
            return false;
        }
    }

    @Override
    public Long getSize(String bucketName, String key) {
        try {
            var s3Resource = s3Template.download(bucketName, key);

            if (!s3Resource.exists()) {
                throw new VideoProcessingException("Arquivo não encontrado no S3: " + key, null);
            }

            long size = s3Resource.contentLength();
            loggerPort.info("Tamanho do arquivo {}: {} bytes", key, size);
            return size;
        } catch (Exception e) {
            loggerPort.error("Erro ao obter tamanho do arquivo: {} - {}", key, e.getMessage());
            throw new VideoProcessingException("Erro ao obter tamanho do arquivo: " + key, e);
        }
    }
}
