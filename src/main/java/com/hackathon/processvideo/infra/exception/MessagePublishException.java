package com.hackathon.processvideo.infra.exception;


public class MessagePublishException extends RuntimeException {

    public MessagePublishException(String message, Exception cause) {
        super(message, cause);
    }

    public MessagePublishException(String message) {
        super(message);
    }

}

