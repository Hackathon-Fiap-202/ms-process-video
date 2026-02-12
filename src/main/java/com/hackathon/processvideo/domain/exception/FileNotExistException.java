package com.hackathon.processvideo.domain.exception;

public class FileNotExistException extends VideoProcessingException {

    public FileNotExistException(String message) {
        super(message);
    }

    public FileNotExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
