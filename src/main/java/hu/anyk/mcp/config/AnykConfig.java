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

        if (!hasResources()) {
            System.err.println("FIGYELMEZTETES: az eroforrasok/ konyvtar nem talalhato itt: "
                + getResourcesPath()
                + "\n  A szervezeti eroforrasok (NAVResources.jar stb.) nelkul a Calculator es a"
                + " nyomtatvany-betoltes nem mukodik helyesen."
                + "\n  Az ANYK_ROOT egy valos ANYK telepitesre mutasson (ami tartalmazza az eroforrasok/-t).");
        }
    }

    public boolean hasResources() {
        File dir = new File(getResourcesPath());
        if (!dir.isDirectory()) return false;
        File[] jars = dir.listFiles((d, n) -> n.matches(".*Resources.*\\.jar"));
        return jars != null && jars.length > 0;
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
