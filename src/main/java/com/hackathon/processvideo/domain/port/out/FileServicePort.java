package com.hackathon.processvideo.domain.port.out;

import java.io.InputStream;

public interface FileServicePort {

    InputStream getFile(String bucketName, String key);

    void deleteFile(String bucketName, String key);

    boolean uploadFile(String bucketName, String key, InputStream file );

    Long getSize(String bucketName, String key);
 }
