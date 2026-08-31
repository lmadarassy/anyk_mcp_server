package hu.anyk.mcp;

import hu.anyk.mcp.adapter.DownloadAdapter;
import hu.anyk.mcp.adapter.DownloadAdapter.ComponentInfo;
import hu.anyk.mcp.adapter.DownloadAdapter.DownloadResult;

import java.util.List;

public class DownloadTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== NAV Download Test ===\n");

        System.out.println("1. Fetching available components from NAV...");
        List<ComponentInfo> all = DownloadAdapter.fetchAvailableComponents();
        System.out.println("   Total components: " + all.size());

        long templates = all.stream().filter(ComponentInfo::isTemplate).count();
        long helps = all.stream().filter(ComponentInfo::isHelp).count();
        long fw = all.stream().filter(ComponentInfo::isFramework).count();
        System.out.println("   Templates: " + templates + ", Helps: " + helps + ", Framework: " + fw);

        System.out.println("\n2. Searching for 2558...");
        ComponentInfo template2558 = null;
        ComponentInfo help2558 = null;
        for (ComponentInfo c : all) {
            if ("2558".equals(c.shortName()) && c.isTemplate()) {
                template2558 = c;
                System.out.println("   Template: " + c.shortName() + " v" + c.version() + " url=" + c.getJarUrl());
            }
            if ("2558".equals(c.shortName()) && c.isHelp()) {
                help2558 = c;
                System.out.println("   Help: " + c.shortName() + " v" + c.version() + " url=" + c.getJarUrl());
            }
        }

        if (template2558 != null) {
            System.out.println("\n3. Downloading and installing 2558 template...");
            String testRoot = "/tmp/opencode/anyk_test";
            new java.io.File(testRoot).mkdirs();
            DownloadResult result = DownloadAdapter.downloadAndInstall(template2558, testRoot);
            System.out.println("   Installed " + result.installedFiles().size() + " files:");
            for (String f : result.installedFiles()) {
                System.out.println("     " + f);
            }

            if (help2558 != null) {
                System.out.println("\n4. Downloading and installing 2558 help...");
                DownloadResult helpResult = DownloadAdapter.downloadAndInstall(help2558, testRoot);
                System.out.println("   Installed " + helpResult.installedFiles().size() + " files:");
                for (String f : helpResult.installedFiles()) {
                    System.out.println("     " + f);
                }
            }
        }

        System.out.println("\n=== Done ===");
    }
}
