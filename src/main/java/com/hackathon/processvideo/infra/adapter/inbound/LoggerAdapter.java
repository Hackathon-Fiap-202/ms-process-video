package com.hackathon.processvideo.infra.adapter.inbound;

import com.hackathon.processvideo.domain.port.out.LoggerPort;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggerAdapter implements LoggerPort {

    private final Logger logger = LoggerFactory.getLogger(LoggerAdapter.class);

    @Override
    public void info(String msg, Object... args) {
        logger.info(msg, sanitize(args));
    }

    @Override
    public void debug(String msg, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(msg, sanitize(args));
        }
    }

    @Override
    public void warn(String msg, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(msg, sanitize(args));
        }
    }

    @Override
    public void error(String msg, Throwable t, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(String.format(msg, sanitize(args)), t);
        }
    }

    @Override
    public void error(String msg, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(msg, sanitize(args));
        }
    }

    private Object[] sanitize(Object... args) {
        if (args == null) {
            return new Object[0];
        }
        return Arrays.stream(args)
                .map(this::cleanString)
                .toArray();
    }

    private Object cleanString(Object input) {
        if (input == null) {
            return null;
        }

        return input.toString().replaceAll("[\n\r]", "_");
    }
}
