package com.vegeai.backend.modules.admin.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.vegeai.backend.modules.admin.dto.AiMonitoringDtos.SystemLogEntry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the most recent server log lines in memory for the admin System Log tab:
 * INFO and above from VEGEAI code, WARN and above from libraries. Cleared on restart.
 */
@Component
public class SystemLogBuffer {

    static final int CAPACITY = 2000;
    private static final int MAX_MESSAGE = 1000;
    private static final String APP_PACKAGE = "com.vegeai";

    private final Deque<SystemLogEntry> entries = new ArrayDeque<>(CAPACITY);
    private final AtomicLong sequence = new AtomicLong();
    private final Instant startedAt = Instant.now();
    private Appender appender;

    @PostConstruct
    void attach() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) return;
        appender = new Appender();
        appender.setContext(context);
        appender.setName("vegeai-admin-system-log");
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    void detach() {
        if (appender == null) return;
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Newest first. */
    public synchronized List<SystemLogEntry> snapshot() {
        List<SystemLogEntry> copy = new ArrayList<>(entries);
        java.util.Collections.reverse(copy);
        return copy;
    }

    synchronized void add(SystemLogEntry entry) {
        if (entries.size() == CAPACITY) entries.removeFirst();
        entries.addLast(entry);
    }

    private static boolean accepted(ILoggingEvent event) {
        Level threshold = event.getLoggerName().startsWith(APP_PACKAGE) ? Level.INFO : Level.WARN;
        return event.getLevel().isGreaterOrEqual(threshold);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MAX_MESSAGE ? value : value.substring(0, MAX_MESSAGE) + "…";
    }

    private class Appender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            if (!accepted(event)) return;
            String logger = event.getLoggerName();
            IThrowableProxy error = event.getThrowableProxy();
            add(new SystemLogEntry(sequence.incrementAndGet(), Instant.ofEpochMilli(event.getTimeStamp()),
                    event.getLevel().toString(), logger.substring(logger.lastIndexOf('.') + 1), logger,
                    event.getThreadName(), truncate(event.getFormattedMessage()),
                    error == null ? null : truncate(error.getClassName() + (error.getMessage() == null ? "" : ": " + error.getMessage()))));
        }
    }
}
