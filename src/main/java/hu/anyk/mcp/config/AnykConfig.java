package hu.anyk.mcp.config;

import java.io.File;
import hu.anyk.mcp.adapter.PropertyListInitializer;

public class AnykConfig {

    private final String anykRoot;

    public AnykConfig(String anykRoot) {
        this.anykRoot = anykRoot;
    }

    public void initialize() {
        System.setProperty("prop.sys.root", anykRoot);
        System.setProperty("prop.sys.helps", "file:///" + anykRoot + "/segitseg");
        System.setProperty("prop.sys.templates", anykRoot + "/nyomtatvanyok");

        new File(getTemplatesPath()).mkdirs();
        new File(getHelpsPath()).mkdirs();
        new File(getUpgradePath()).mkdirs();

        PropertyListInitializer.ensureInitialized(anykRoot);
    }

    public String getAnykRoot() {
        return anykRoot;
    }

    public String getTemplatesPath() {
        return anykRoot + "/nyomtatvanyok";
    }

    public String getHelpsPath() {
        return anykRoot + "/segitseg";
    }

    public String getUpgradePath() {
        return anykRoot + "/upgrade";
    }

    public String getXsdPath() {
        return anykRoot + "/xsd";
    }

    public String getResourcesPath() {
        return anykRoot + "/eroforrasok";
    }
}
