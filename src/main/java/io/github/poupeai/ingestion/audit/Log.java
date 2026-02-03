package io.github.poupeai.ingestion.audit;

import org.slf4j.Logger;
import org.slf4j.MDC;

public class Log {

    public static void event(Logger logger, String eventType, String message, Object... args) {
        if (logger.isInfoEnabled()) {
            MDC.put("event.type", eventType);
            try {
                logger.info(message, args);
            } finally {
                MDC.remove("event.type");
            }
        }
    }

    public static void error(Logger logger, String eventType, String message, Throwable throwable) {
        if (logger.isErrorEnabled()) {
            MDC.put("event.type", eventType);
            try {
                logger.error(message, throwable);
            } finally {
                MDC.remove("event.type");
            }
        }
    }

    public static void warn(Logger logger, String eventType, String message, Object... args) {
        if (logger.isWarnEnabled()) {
            MDC.put("event.type", eventType);
            try {
                logger.warn(message, args);
            } finally {
                MDC.remove("event.type");
            }
        }
    }
}