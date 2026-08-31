package hu.anyk.mcp.session;

import hu.anyk.mcp.config.AnykConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final AnykConfig config;
    private final Map<String, FormSession> sessions = new ConcurrentHashMap<>();

    public SessionManager(AnykConfig config) {
        this.config = config;
    }

    public FormSession createSession() {
        String id = UUID.randomUUID().toString();
        FormSession session = new FormSession(id);
        sessions.put(id, session);
        return session;
    }

    public FormSession getSession(String sessionId) {
        FormSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        session.touch();
        return session;
    }

    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public void closeAll() {
        sessions.clear();
    }

    public AnykConfig getConfig() {
        return config;
    }
}
