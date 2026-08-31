package hu.anyk.mcp.adapter;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class DownloadAdapter {

    private static final String DEFAULT_NAV_UPDATE_URL = "https://nav.gov.hu/abev/abev_new";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    public record ComponentInfo(
        String category,
        String org,
        String shortName,
        String version,
        String description,
        String baseUrl,
        List<String> files
    ) {
        public boolean isTemplate() { return "Template".equals(category); }
        public boolean isHelp() { return "Help".equals(category); }
        public boolean isFramework() { return "Framework".equals(category); }

        public String getJarUrl() {
            for (String f : files) {
                if (f.endsWith(".jar")) return baseUrl + f;
            }
            return null;
        }
    }

    public static List<ComponentInfo> fetchAvailableComponents(String updateUrl) throws Exception {
        if (updateUrl == null || updateUrl.isEmpty()) {
            updateUrl = DEFAULT_NAV_UPDATE_URL;
        }

        URL url = new URI(updateUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " from " + updateUrl);
        }

        String xml;
        try (InputStream is = conn.getInputStream()) {
            xml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        return parseEnykXml(xml);
    }

    public static List<ComponentInfo> fetchAvailableComponents() throws Exception {
        return fetchAvailableComponents(DEFAULT_NAV_UPDATE_URL);
    }

    static List<ComponentInfo> parseEnykXml(String xml) throws Exception {
        List<ComponentInfo> result = new ArrayList<>();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        SAXParser parser = factory.newSAXParser();

        parser.parse(new InputSource(new StringReader(xml)), new DefaultHandler() {
            String currentElement = "";
            String parentElement = "";
            String category, org, shortName, version, description, baseUrl;
            List<String> files;
            boolean inFiles = false;

            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
                currentElement = qName;
                if ("nyomtatvany".equals(qName) || "utmutato".equals(qName) || "keretprogram".equals(qName)) {
                    parentElement = qName;
                    category = null; org = null; shortName = null; version = null;
                    description = null; baseUrl = null; files = new ArrayList<>();
                } else if ("files".equals(qName)) {
                    inFiles = true;
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if ("nyomtatvany".equals(qName) || "utmutato".equals(qName) || "keretprogram".equals(qName)) {
                    if (shortName != null) {
                        result.add(new ComponentInfo(category, org, shortName, version, description, baseUrl, files));
                    }
                    parentElement = "";
                } else if ("files".equals(qName)) {
                    inFiles = false;
                }
                currentElement = "";
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (parentElement.isEmpty()) return;
                String text = new String(ch, start, length).trim();
                if (text.isEmpty()) return;

                switch (currentElement) {
                    case "kategoria" -> category = text;
                    case "szervezet" -> org = "APEH".equals(text) ? "NAV" : text;
                    case "rovidnev" -> shortName = text;
                    case "verzio" -> version = text;
                    case "elnevezes" -> description = (description == null ? text : description + text);
                    case "url" -> baseUrl = text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
                    case "file" -> { if (inFiles) files.add(text); }
                }
            }
        });

        return result;
    }

    public static DownloadResult downloadAndInstall(ComponentInfo component, String anykRoot) throws Exception {
        String jarUrl = component.getJarUrl();
        if (jarUrl == null) {
            throw new IOException("No JAR file found for " + component.shortName());
        }

        Path tempFile = Files.createTempFile("anyk_download_", ".jar");
        try {
            downloadFile(jarUrl, tempFile);
            return extractJar(tempFile, anykRoot, component);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void downloadFile(String urlStr, Path dest) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " downloading " + urlStr);
        }

        try (InputStream is = conn.getInputStream();
             OutputStream os = Files.newOutputStream(dest)) {
            is.transferTo(os);
        }
    }

    private static DownloadResult extractJar(Path jarPath, String anykRoot, ComponentInfo component) throws Exception {
        List<String> installedFiles = new ArrayList<>();
        String templatesDir = anykRoot + "/nyomtatvanyok";
        String helpsDir = anykRoot + "/segitseg";

        new File(templatesDir).mkdirs();
        new File(helpsDir).mkdirs();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                String destPath = null;
                if (name.startsWith("application/nyomtatvanyok/") && !entry.isDirectory()) {
                    String relative = name.substring("application/nyomtatvanyok/".length());
                    destPath = templatesDir + "/" + relative;
                } else if (name.startsWith("application/segitseg/") && !entry.isDirectory()) {
                    String relative = name.substring("application/segitseg/".length());
                    destPath = helpsDir + "/" + relative;
                }

                if (destPath != null) {
                    File destFile = new File(destPath);
                    destFile.getParentFile().mkdirs();
                    try (InputStream is = jar.getInputStream(entry);
                         OutputStream os = new FileOutputStream(destFile)) {
                        is.transferTo(os);
                    }
                    installedFiles.add(destPath);
                }
            }
        }

        return new DownloadResult(component.shortName(), component.version(), component.category(),
            installedFiles, null);
    }

    public record DownloadResult(
        String name,
        String version,
        String category,
        List<String> installedFiles,
        String error
    ) {}
}
