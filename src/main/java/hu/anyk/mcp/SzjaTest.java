package hu.anyk.mcp;

import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.anyk.mcp.adapter.DownloadAdapter;
import hu.anyk.mcp.adapter.DownloadAdapter.*;
import hu.anyk.mcp.adapter.SimpleXmlSaver;
import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;

import java.io.*;
import java.util.*;

public class SzjaTest {

    static final String ROOT = "/tmp/opencode/anyk_szja";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        System.out.println("=== 2553 SZJA End-to-End Test ===\n");

        File temFile = new File(ROOT + "/nyomtatvanyok/NAV_2553_9_0.tem.enyk");
        if (!temFile.exists()) {
            System.out.println("Downloading 2553...");
            new File(ROOT + "/nyomtatvanyok").mkdirs();
            new File(ROOT + "/segitseg").mkdirs();
            List<ComponentInfo> all = DownloadAdapter.fetchAvailableComponents();
            for (ComponentInfo c : all) {
                if ("2553".equals(c.shortName()) && c.isTemplate())
                    DownloadAdapter.downloadAndInstall(c, ROOT);
                if ("2553".equals(c.shortName()) && c.isHelp())
                    DownloadAdapter.downloadAndInstall(c, ROOT);
            }
        }

        System.out.println("1. Loading template...");
        BookModel bm = BookModelAdapter.loadTemplate(temFile);
        BookModelAdapter.addEmptyForm(bm, 0);
        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
        FormModel fm = (FormModel) bm.forms.get(0);
        System.out.println("   Form: " + fm.id + " pages=" + fm.pages.size() + " fields=" + fm.fids.size());

        System.out.println("\n2. Filling 25SZJA form (simple salary income)...");

        // === ELOLAP (Page 0) - Azonositok ===
        // 010001B001A: Adoszam (########-#-##) - nem kell maganszemelynek
        // 010001B004A: Adoazonosito jel (##########)
        set(ds, "010001B004A", "8412345678");

        // === FOLAP (Page 1) ===
        // B blokk - Szemelyi adatok (readonly, az E001-E002 tartalmaznak adoazonositot)
        // E blokk - Cim, szemelyes adatok

        // E001: Adoazonosito jel (##########)
        set(ds, "0A0001E001A", "8412345678");
        // E002: TAJ szam (##########)
        set(ds, "0A0001E002A", "012345678");
        // E003: Allampolgarsag (HU)
        set(ds, "0A0001E003A", "HU");
        // E005: Iranyitoszam (combo)
        set(ds, "0A0001E005A", "1011");
        // E006: Telepules nev
        set(ds, "0A0001E006A", "Budapest");
        // E007: Kozterulet neve
        set(ds, "0A0001E007A", "Fo");
        // E008: Kozterulet jellege (1=utca, 2=ut)
        set(ds, "0A0001E008A", "1");
        // E009: Hazszam
        set(ds, "0A0001E009A", "1");
        // E011: Szuletesi datum
        set(ds, "0A0001E011A", "1985.06.15");
        // E019: Nev (vezeteknev)
        set(ds, "0A0001E019A", "Teszt");
        // E020: Nev tipus combo
        // E021: Keresztnev
        set(ds, "0A0001E021A", "Elek");
        // E022: Szuletesi vezeteknev
        set(ds, "0A0001E022A", "Teszt");
        // E023: Szuletesi keresztnev
        set(ds, "0A0001E023A", "Elek");
        // E024: Anyja neve
        set(ds, "0A0001E024A", "Minta Maria");
        // E025: Szuletesi hely
        set(ds, "0A0001E025A", "Budapest");

        // F blokk - Bevallas jellege
        // F026: Bevallas jellege: H=helyes, O=onellenorzes
        set(ds, "0A0001F026A", "H");

        // G blokk - Bankszamla (visszautalaashoz)
        // G003: Bankszamlaszam (########-########-########)
        set(ds, "0A0001G003A", "11773016-11111118-00000000");
        // G007: Koltsegvetes ev (####)
        set(ds, "0A0001G007A", "2025");

        // H blokk - Nyilatkozatok
        // H001: Elso nyilatkozat - check
        // H003: Telefonszam
        set(ds, "0A0001H003A", "301234567");

        System.out.println("   Header fields set.");

        // === Osszes kitoltott mezo ===
        System.out.println("\n3. Filled fields:");
        int filled = 0;
        Enumeration<String> keys = fm.fids.keys();
        List<String> fidList = new ArrayList<>();
        while (keys.hasMoreElements()) fidList.add(keys.nextElement());
        Collections.sort(fidList);
        for (String fid : fidList) {
            String val = ds.get(new Object[]{0, fid});
            if (val != null && !val.isEmpty()) {
                filled++;
                System.out.println("   " + fid + " = '" + val + "'");
            }
        }
        System.out.println("   Total filled: " + filled);

        // === Mentes ===
        System.out.println("\n4. Saving...");
        String outPath = ROOT + "/2553_test.xml";
        SimpleXmlSaver.save(bm, outPath);
        System.out.println("   Saved: " + new File(outPath).length() + " bytes");

        // === Validacio ===
        System.out.println("\n5. Validation...");
        try {
            var checker = hu.piller.enykp.alogic.fileutil.DataChecker.getInstance();
            checker.superCheck(bm, true);
            System.out.println("   hasError=" + bm.hasError);
            if (bm.errorlist != null && bm.errorlist.size() > 0) {
                System.out.println("   Errors (" + bm.errorlist.size() + "):");
                for (int i = 0; i < Math.min(bm.errorlist.size(), 30); i++)
                    System.out.println("     " + bm.errorlist.get(i));
                if (bm.errorlist.size() > 30)
                    System.out.println("     ... and " + (bm.errorlist.size() - 30) + " more");
            }
            if (bm.warninglist != null && bm.warninglist.size() > 0) {
                System.out.println("   Warnings (" + bm.warninglist.size() + "):");
                for (int i = 0; i < Math.min(bm.warninglist.size(), 10); i++)
                    System.out.println("     " + bm.warninglist.get(i));
            }
        } catch (Exception e) {
            System.out.println("   Validation exception: " + e.getMessage());
        }

        bm.destroy();
        System.out.println("\n=== Done ===");
    }

    static void set(GUI_Datastore ds, String fid, String value) {
        try {
            ds.set(new Object[]{0, fid}, value);
        } catch (Exception e) {
            System.out.println("   ERROR setting " + fid + ": " + e.getMessage());
        }
    }
}
