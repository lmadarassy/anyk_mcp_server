package hu.anyk.mcp.session;

import java.io.File;

public class FormSession {

    private final String sessionId;
    private Object bookModel;
    private Object cachedCollection;
    private File templateFile;
    private String orgId;
    private String templateId;
    private String formName;
    private String helpDir;
    private long createdAt;
    private long lastAccessedAt;

    public FormSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
    }

    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public Object getBookModel() { return bookModel; }
    public void setBookModel(Object bookModel) { this.bookModel = bookModel; }
    public Object getCachedCollection() { return cachedCollection; }
    public void setCachedCollection(Object cachedCollection) { this.cachedCollection = cachedCollection; }
    public File getTemplateFile() { return templateFile; }
    public void setTemplateFile(File templateFile) { this.templateFile = templateFile; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public String getHelpDir() { return helpDir; }
    public void setHelpDir(String helpDir) { this.helpDir = helpDir; }
    public long getCreatedAt() { return createdAt; }
    public long getLastAccessedAt() { return lastAccessedAt; }
}
