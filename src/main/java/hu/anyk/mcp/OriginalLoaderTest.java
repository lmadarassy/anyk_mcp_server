package hu.anyk.mcp;

import hu.piller.enykp.util.base.PropertyList;
import hu.piller.enykp.interfaces.IPropertyList;
import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.piller.enykp.datastore.Elem;

import java.io.File;

public class OriginalLoaderTest {

    // A valos ANYK telepites (eroforrasok/ JAR-okkal)
    static final String ANYK_INSTALL = "/mnt/c/Users/elszmad/Downloads/abev/abevjava";
    static final String TEMPLATE = "/tmp/opencode/anyk_szja/nyomtatvanyok/NAV_2553_9_0.tem.enyk";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        System.out.println("=== Original BookModel loader test (with real eroforrasok) ===\n");

        // 1. PropertyList init - a valos telepites root-javal
        IPropertyList pl = PropertyList.getInstance();
        pl.set("prop.sys.root", ANYK_INSTALL);
        pl.set("prop.usr.root", "/tmp/opencode/anyk_usr");
        pl.set("prop.usr.tmp", "tmp");
        pl.set("prop.usr.settings", "settings");
        pl.set("prop.usr.krdir", "/tmp/opencode/anyk_usr/kr");
        pl.set("prop.usr.ds_src", "");
        pl.set("prop.usr.saves", "saves");
        pl.set("prop.dynamic.debug", Boolean.FALSE);
        pl.set("prop.dynamic.signWithExternalTool", null);
        new File("/tmp/opencode/anyk_usr/tmp").mkdirs();
        new File("/tmp/opencode/anyk_usr/settings").mkdirs();
        new File("/tmp/opencode/anyk_usr/kr").mkdirs();
        new File("/tmp/opencode/anyk_usr/saves").mkdirs();

        // Adozoi szerep beallitasa (normalisan a GUI inditas allitja be)
        hu.piller.enykp.gui.framework.MainFrame.role = "0";
        System.out.println("   MainFrame.role = 0 (adozo)");

        // Mentesi konyvtar beallitasa a SettingsStore-ban (getDsPath ezt hasznalja)
        String saveDir = "/tmp/opencode/anyk_out/";
        new File(saveDir).mkdirs();
        hu.piller.enykp.alogic.settingspanel.SettingsStore.getInstance()
            .set("gui", "digitális_aláírás", saveDir);
        System.out.println("   save dir = " + saveDir);

        System.out.println("1. prop.sys.root = " + ANYK_INSTALL);
        System.out.println("   eroforrasok exists: " + new File(ANYK_INSTALL + "/eroforrasok").exists());

        // 2. Ellenorizzuk az OrgInfo-t
        System.out.println("\n2. Testing OrgInfo...");
        try {
            var orgInfo = hu.piller.enykp.alogic.orghandler.OrgInfo.getInstance();
            Object orgList = orgInfo.getOrgList();
            System.out.println("   orgList = " + orgList);
            if (orgList instanceof java.util.Hashtable ht) {
                System.out.println("   orgs: " + ht.keySet());
                Object nav = ht.get("NAV");
                System.out.println("   NAV resource: " + (nav != null ? "FOUND" : "NULL"));
                if (nav instanceof hu.piller.enykp.alogic.orghandler.OrgResource or) {
                    System.out.println("   NAV orgCheckValid: " + or.getOrgCheckValid());
                }
            }
        } catch (Exception e) {
            System.out.println("   OrgInfo FAIL: " + e.getMessage());
            e.printStackTrace();
        }

        // 3. Eredeti BookModel(File) konstruktor
        System.out.println("\n3. Testing original BookModel(File) constructor...");
        try {
            BookModel bm = new BookModel(new File(TEMPLATE), true);  // silent=true
            System.out.println("   id = " + bm.id);
            System.out.println("   hasError = " + bm.hasError);
            System.out.println("   errormsg = " + bm.errormsg);
            System.out.println("   cc = " + (bm.cc != null ? "exists size=" + bm.cc.size() : "null"));
            System.out.println("   calculator = " + (bm.calculator != null ? "EXISTS" : "null"));

            if (bm.cc != null && bm.cc.size() == 0 && bm.forms != null && !bm.forms.isEmpty()) {
                System.out.println("\n4. Testing addForm() (creates Elem + triggers calculator)...");
                FormModel fm = (FormModel) bm.forms.get(0);
                bm.addForm(fm);
                System.out.println("   cc.size() = " + bm.cc.size());
                System.out.println("   activeObject = " + bm.cc.getActiveObject());

                GUI_Datastore ds = null;
                Object active = bm.cc.getActiveObject();
                if (active instanceof Elem elem && elem.getRef() instanceof GUI_Datastore g) ds = g;
                System.out.println("   datastore = " + (ds != null ? "OK" : "null"));

                if (ds != null) {
                    System.out.println("\n5. Setting fields + testing calculator...");
                    ds.set(new Object[]{0, "0A0001E001A"}, "8413921635");
                    ds.set(new Object[]{0, "0B0001C0001DA"}, "20000000");
                    System.out.println("   Set berjovedelem: 20000000");

                    // Trigger calculations
                    try {
                        hu.piller.enykp.alogic.calculator.CalculatorManager.getInstance().form_calc();
                        System.out.println("   form_calc() OK");
                    } catch (Exception e) {
                        System.out.println("   form_calc() fail: " + e.getMessage());
                    }

                    // Read a calculated field (e.g., osszevont jovedelem)
                    String calc1 = ds.get(new Object[]{0, "0B0001C0007DA"});
                    System.out.println("   Calculated field 0B0001C0007DA = " + calc1);

                    System.out.println("\n6. Testing original EnykXmlSaver...");
                    try {
                        hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver saver =
                            new hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver(bm);
                        // Csupasz fajlnev - a mentesi konyvtart a SettingsStore adja (getDsPath)
                        String bareName = "2553_original_test";
                        boolean saved = saver.save(bareName, true);
                        System.out.println("   EnykXmlSaver.save() = " + saved);
                        System.out.println("   suffix = " + saver.getFileNameSuffix());
                        File outDir = new File("/tmp/opencode/anyk_out/");
                        File[] produced = outDir.listFiles();
                        if (produced != null) {
                            for (File pf : produced) {
                                System.out.println("   produced: " + pf.getName() + " (" + pf.length() + " bytes)");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("   EnykXmlSaver FAIL: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("   BookModel FAIL: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== Done ===");
    }
}
