package hu.anyk.mcp.adapter;

import hu.piller.enykp.util.base.PropertyList;
import hu.piller.enykp.interfaces.IPropertyList;
import hu.piller.enykp.gui.framework.MainFrame;
import hu.piller.enykp.alogic.settingspanel.SettingsStore;

import java.io.File;

public class PropertyListInitializer {

    private static boolean initialized = false;

    public static synchronized void ensureInitialized(String anykRoot) {
        if (initialized) return;

        IPropertyList pl = PropertyList.getInstance();
        pl.set("prop.dynamic.debug", Boolean.FALSE);
        pl.set("prop.dynamic.signWithExternalTool", null);
        pl.set("prop.dynamic.dirty2", Boolean.FALSE);
        pl.set("prop.dynamic.hasNewTemplate", Boolean.FALSE);
        pl.set("prop.usr.root", anykRoot);
        pl.set("prop.usr.tmp", "tmp");
        pl.set("prop.usr.settings", "settings");
        pl.set("prop.usr.saves", "saves");
        pl.set("prop.usr.import", "import");
        pl.set("prop.usr.krdir", anykRoot + File.separator + "kr");
        pl.set("prop.usr.ds_src", "");
        pl.set("prop.usr.naplo", anykRoot + File.separator + "log");
        pl.set("prop.sys.root", anykRoot);
        pl.set("prop.sys.helps", "file:///" + anykRoot + "/segitseg");
        pl.set("prop.gui.screen.dpi", 96);
        pl.set("prop.gui.screen.maxx", 1920);
        pl.set("prop.gui.screen.maxy", 1080);
        pl.set("prop.gui.item.height", 20);
        pl.set("prop.gui.font.size", 12);
        pl.set("veto_check", Boolean.FALSE);
        pl.set("fieldcheckdialog", Boolean.FALSE);
        pl.set("foadatcalculation", Boolean.FALSE);

        new File(anykRoot, "tmp").mkdirs();
        new File(anykRoot, "settings").mkdirs();
        new File(anykRoot, "saves").mkdirs();
        new File(anykRoot, "kr").mkdirs();
        new File(anykRoot, "log").mkdirs();

        // Adozoi szerep (normalisan a GUI inditas allitja be). "0" = adozo.
        if (MainFrame.role == null) {
            MainFrame.role = "0";
        }

        initialized = true;
    }

    /**
     * Beallitja a mentesi konyvtarat, amit az EnykXmlSaver.getDsPath() hasznal.
     * A path vegen legyen elvalasztojel.
     */
    public static void setSaveDir(String dir) {
        if (!dir.endsWith(File.separator) && !dir.endsWith("/")) {
            dir = dir + File.separator;
        }
        new File(dir).mkdirs();
        SettingsStore.getInstance().set("gui", "digitális_aláírás", dir);
    }
}
