package hu.anyk.mcp;

import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;

import java.io.File;
import java.util.*;

public class FormFillZeroTest {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        String templatePath = args.length > 0 ? args[0] : "/tmp/opencode/test_template.tem.enyk";
        File templateFile = new File(templatePath);

        System.out.println("=== 2558 Zero Turnover Fill Test ===\n");

        BookModel bm = BookModelAdapter.loadTemplate(templateFile);
        BookModelAdapter.addEmptyForm(bm, 0);
        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);

        if (ds == null) {
            System.out.println("FAIL: no datastore");
            return;
        }

        // === FŐLAP (Page 0) - Azonosító adatok ===
        System.out.println("1. Filling header fields (forlap)...");

        // Adószám: 12345678-1-41 (fiktiv, de helyes formatum)
        setField(ds, "0A0001C001A", "12345678-1-41");
        // Adóazonosító jel
        setField(ds, "0A0001C002A", "8012345678");
        // TAJ szám
        setField(ds, "0A0001C003A", "012345678");

        // Név (az adózo neve)
        setField(ds, "0A0001C006A", "Teszt");
        setField(ds, "0A0001C007A", "Elek");

        // Orszag
        setField(ds, "0A0001C008A", "HU");
        // Iranyitoszam
        setField(ds, "0A0001C010A", "1011");
        // Telepules
        setField(ds, "0A0001C011A", "Budapest");
        // Kozterulet neve
        setField(ds, "0A0001C012A", "Fo");
        // Kozterulet jellege
        setField(ds, "0A0001C013A", "utca");
        // Hazszam
        setField(ds, "0A0001C015A", "1");

        // Adózó típusa (1=egyéni vállalkozó, 2=mezőgazdasági őstermelő)
        setField(ds, "0A0001C014A", "1");

        // Bevallási időszak (2025 Q1 - negyedeves bevallas)
        setField(ds, "0A0001D001A", "2025.01.01-");
        setField(ds, "0A0001D002A", "2025.03.31");

        // Bevallás típusa: H=havi, O=összevont (negyedéves)
        setField(ds, "0A0001D003A", "O");

        // Bevallás jellege: E=első, N=nem első
        setField(ds, "0A0001D005A", "E");

        // Bevallás gyakorisága: 1=havi, 2=negyedéves, 3=éves, 4=eseti, 5=évközi
        setField(ds, "0A0001D006A", "2");

        // Főlap eredménye: H=helyes, N=nincs, stb.
        setField(ds, "0A0001D004A", "N");

        System.out.println("   Header fields set.\n");

        // === NY lap (Page 1) - Nyilatkozat ===
        System.out.println("2. Filling NY page...");
        // Telefonszam
        setField(ds, "0A0001E005A", "301234567");
        System.out.println("   NY page set.\n");

        // === Zero forgalom - nem toltunk ki reszletes lapokat ===
        System.out.println("3. Zero turnover: leaving detail pages empty.\n");

        // === Osszes kitoltott mezo kiirasa ===
        System.out.println("4. Filled fields summary:");
        FormModel fm = (FormModel) bm.forms.get(0);
        int filledCount = 0;
        Enumeration<String> keys = fm.fids.keys();
        while (keys.hasMoreElements()) {
            String fid = keys.nextElement();
            String val = ds.get(new Object[]{Integer.valueOf(0), fid});
            if (val != null && !val.isEmpty()) {
                DataFieldModel df = (DataFieldModel) fm.fids.get(fid);
                System.out.println("   " + fid + " = '" + val + "' (type=" + df.type + ", ro=" + df.readonly + ")");
                filledCount++;
            }
        }
        System.out.println("   Total filled: " + filledCount + "\n");

        // === Mentes ===
        System.out.println("5. Saving to XML...");
        String outputPath = "/tmp/opencode/2558_zero_test.xml";
        try {
            hu.anyk.mcp.adapter.SimpleXmlSaver.save(bm, outputPath);
            File outFile = new File(outputPath);
            System.out.println("   Save OK, size: " + outFile.length() + " bytes");
        } catch (Exception e) {
            System.out.println("   Save failed: " + e.getMessage());
            e.printStackTrace();
        }

        // === Validacio ===
        System.out.println("\n6. Validation...");
        try {
            hu.piller.enykp.alogic.fileutil.DataChecker checker = 
                hu.piller.enykp.alogic.fileutil.DataChecker.getInstance();
            Object result = checker.superCheck(bm, true);
            System.out.println("   hasError=" + bm.hasError);
            if (bm.errorlist != null) {
                System.out.println("   Errors (" + bm.errorlist.size() + "):");
                for (int i = 0; i < Math.min(bm.errorlist.size(), 20); i++) {
                    System.out.println("     " + bm.errorlist.get(i));
                }
                if (bm.errorlist.size() > 20) {
                    System.out.println("     ... and " + (bm.errorlist.size() - 20) + " more");
                }
            }
            if (bm.warninglist != null && bm.warninglist.size() > 0) {
                System.out.println("   Warnings (" + bm.warninglist.size() + "):");
                for (int i = 0; i < Math.min(bm.warninglist.size(), 10); i++) {
                    System.out.println("     " + bm.warninglist.get(i));
                }
            }
        } catch (Exception e) {
            System.out.println("   Validation failed: " + e.getMessage());
            e.printStackTrace();
        }

        bm.destroy();
        System.out.println("\n=== Test Complete ===");
    }

    static void setField(GUI_Datastore ds, String fid, String value) {
        try {
            ds.set(new Object[]{Integer.valueOf(0), fid}, value);
            String readback = ds.get(new Object[]{Integer.valueOf(0), fid});
            if (!value.equals(readback)) {
                System.out.println("   WARNING: " + fid + " readback mismatch: set='" + value + "' got='" + readback + "'");
            }
        } catch (Exception e) {
            System.out.println("   ERROR setting " + fid + ": " + e.getMessage());
        }
    }
}
