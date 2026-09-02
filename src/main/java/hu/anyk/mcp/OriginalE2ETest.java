package hu.anyk.mcp;

import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.anyk.mcp.adapter.PropertyListInitializer;
import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver;

import java.io.*;
import java.util.*;

/**
 * End-to-end teszt kizarolag az EREDETI abevjava.jar osztalyokkal:
 * PropertyList init -> BookModel(File) -> addForm() -> Calculator ->
 * mezo kitoltes -> EnykXmlSaver.
 */
public class OriginalE2ETest {

    static final String ANYK_INSTALL = "/mnt/c/Users/elszmad/Downloads/abev/abevjava";
    static final String TEMPLATE = "/tmp/opencode/anyk_szja/nyomtatvanyok/NAV_2553_9_0.tem.enyk";
    static final String OUT_DIR = "/tmp/opencode/anyk_e2e_out";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        System.out.println("=== Original-jar E2E test (2553 SZJA) ===\n");

        // 1. Inicializalas (ez az egyetlen "sajat" resz - property setup)
        PropertyListInitializer.ensureInitialized(ANYK_INSTALL);
        PropertyListInitializer.setSaveDir(OUT_DIR);
        System.out.println("1. Init OK (root=" + ANYK_INSTALL + ")");

        // 2. Betoltes - EREDETI BookModel(File)
        BookModel bm = BookModelAdapter.loadTemplate(new File(TEMPLATE));
        BookModelAdapter.addEmptyForm(bm);
        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
        System.out.println("2. BookModel loaded: id=" + bm.id
            + " hasError=" + bm.hasError
            + " calculator=" + (bm.calculator != null ? "OK" : "null")
            + " cc.size=" + bm.cc.size());

        // 3. Kitoltes (Madarassy Laszlo Tibor adatai)
        set(ds, "010001B001A", "60187590-1-33");
        set(ds, "010001B004A", "8413921635");
        set(ds, "0A0001E001A", "8413921635");
        set(ds, "0A0001E002A", "037098902");
        set(ds, "0A0001E003A", "HU");
        set(ds, "0A0001E005A", "2030");
        set(ds, "0A0001E006A", "Érd");
        set(ds, "0A0001E007A", "Tusnádi");
        set(ds, "0A0001E008A", "1");
        set(ds, "0A0001E009A", "32");
        set(ds, "0A0001E011A", "1980.04.30");
        set(ds, "0A0001E019A", "Madarassy");
        set(ds, "0A0001E021A", "László Tibor");
        set(ds, "0A0001E022A", "Madarassy");
        set(ds, "0A0001E023A", "László Tibor");
        set(ds, "0A0001E024A", "Fehér Zsuzsanna");
        set(ds, "0A0001E025A", "Budapest");
        set(ds, "0A0001G003A", "10404247-91529390-01540000");
        // Jovedelem: A lap 1. sor "d" oszlop - berjovedelem
        set(ds, "0B0001C0001DA", "20000000");
        System.out.println("3. Fields set");

        // 4. Calculator futtatasa (EREDETI CalculatorManager)
        try {
            hu.piller.enykp.alogic.calculator.CalculatorManager.getInstance().form_calc();
            System.out.println("4. Calculator form_calc() OK");
        } catch (Exception e) {
            System.out.println("4. Calculator fail: " + e.getMessage());
        }

        // Nehany szamitott mezo kiolvasasa
        System.out.println("   Szamitott mezok:");
        printCalc(ds, "0A0001A018A", "osszevont adoalap (18. sor)");
        printCalc(ds, "0A0001A054A", "szamitott adó (54. sor)");
        printCalc(ds, "0A0001A079A", "fizetendo adó (79. sor)");

        // 5. Mentes - EREDETI EnykXmlSaver
        new File(OUT_DIR).mkdirs();
        EnykXmlSaver saver = new EnykXmlSaver(bm);
        boolean ok = saver.save("2553_madarassy_e2e", true);
        File produced = new File(OUT_DIR, "2553_madarassy_e2e" + saver.getFileNameSuffix());
        System.out.println("5. EnykXmlSaver.save() = " + ok
            + " -> " + produced.getName()
            + " (" + (produced.exists() ? produced.length() + " bytes" : "MISSING") + ")");

        // 6. Validacio - EREDETI DataChecker
        try {
            hu.piller.enykp.alogic.fileutil.DataChecker.getInstance().superCheck(bm, true);
            System.out.println("6. Validacio: hasError=" + bm.hasError
                + " errors=" + (bm.errorlist != null ? bm.errorlist.size() : 0)
                + " warnings=" + (bm.warninglist != null ? bm.warninglist.size() : 0));
        } catch (Exception e) {
            System.out.println("6. Validacio fail: " + e.getMessage());
        }

        bm.destroy();
        System.out.println("\n=== Done ===");
    }

    static void set(GUI_Datastore ds, String fid, String value) {
        try {
            ds.set(new Object[]{0, fid}, value);
        } catch (Exception e) {
            System.out.println("   ERROR " + fid + ": " + e.getMessage());
        }
    }

    static void printCalc(GUI_Datastore ds, String fid, String label) {
        try {
            String v = ds.get(new Object[]{0, fid});
            System.out.println("     " + label + " [" + fid + "] = " + v);
        } catch (Exception e) {
            System.out.println("     " + label + " [" + fid + "] = ERROR");
        }
    }
}
