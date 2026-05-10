package com.example.hazedetector.server.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;

@Service
public class ClientSessionService {
    public static final String HEADER_NAME = "X-Client-Session-Id";
    public static final String COOKIE_NAME = "Haze-Session";
    public static final Duration TTL = Duration.ofDays(30);

    private static final Path DATA_DIR = Path.of("data");
    private static final Path SESSIONS_FILE = DATA_DIR.resolve("sessions.properties");

    public synchronized ClientSession resolve(String requestedSessionId) {
        Properties sessions = loadSessions();
        Instant now = Instant.now();
        purgeExpired(sessions, now);

        String sessionId = cleanSessionId(requestedSessionId);
        if (!sessionId.isBlank() && isActive(sessions, sessionId, now)) {
            refresh(sessions, sessionId, now);
            saveSessions(sessions);
            return new ClientSession(sessionId, false);
        }

        String newSessionId = newSessionId(sessions);
        refresh(sessions, newSessionId, now);
        saveSessions(sessions);
        return new ClientSession(newSessionId, true);
    }

    private Properties loadSessions() {
        Properties sessions = new Properties();
        try {
            Files.createDirectories(DATA_DIR);
            if (Files.exists(SESSIONS_FILE)) {
                try (InputStream input = Files.newInputStream(SESSIONS_FILE)) {
                    sessions.load(input);
                }
            }
            return sessions;
        } catch (IOException error) {
            throw new IllegalStateException("读取客户端 session 失败", error);
        }
    }

    private void saveSessions(Properties sessions) {
        try {
            Files.createDirectories(DATA_DIR);
            try (OutputStream output = Files.newOutputStream(SESSIONS_FILE)) {
                sessions.store(output, "Haze detector client sessions");
            }
        } catch (IOException error) {
            throw new IllegalStateException("保存客户端 session 失败", error);
        }
    }

    private boolean isActive(Properties sessions, String sessionId, Instant now) {
        String expiresAt = sessions.getProperty(sessionId, "");
        try {
            return Instant.parse(expiresAt).isAfter(now);
        } catch (Exception error) {
            return false;
        }
    }

    private void refresh(Properties sessions, String sessionId, Instant now) {
        sessions.setProperty(sessionId, now.plus(TTL).toString());
    }

    private void purgeExpired(Properties sessions, Instant now) {
        Iterator<String> names = sessions.stringPropertyNames().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (!isActive(sessions, name, now)) {
                sessions.remove(name);
            }
        }
    }

    private String newSessionId(Properties sessions) {
        String sessionId;
        do {
            sessionId = UUID.randomUUID().toString();
        } while (sessions.containsKey(sessionId));
        return sessionId;
    }

    private String cleanSessionId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sessionId = value.trim();
        if (!sessionId.matches("[A-Za-z0-9._-]{16,80}")) {
            return "";
        }
        return sessionId;
    }

    public record ClientSession(String id, boolean created) {
    }
}
