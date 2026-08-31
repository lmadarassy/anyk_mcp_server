package hu.anyk.mcp.adapter;

import hu.piller.enykp.util.base.PropertyList;
import hu.piller.enykp.interfaces.IPropertyList;

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
        pl.set("prop.usr.saves", "saves");
        pl.set("prop.usr.import", "import");
        pl.set("prop.usr.krdir", anykRoot + "/kr");
        pl.set("prop.usr.ds_src", "");
        pl.set("prop.usr.naplo", anykRoot + "/log");
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

        initialized = true;
    }
}
