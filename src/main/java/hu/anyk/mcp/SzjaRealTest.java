package hu.anyk.mcp;

import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.anyk.mcp.adapter.SimpleXmlSaver;
import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;

import java.io.*;
import java.util.*;

public class SzjaRealTest {

    static final String ROOT = "/tmp/opencode/anyk_szja";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        System.out.println("=== 25SZJA Madarassy Laszlo Tibor - 2025 ===\n");

        File temFile = new File(ROOT + "/nyomtatvanyok/NAV_2553_9_0.tem.enyk");
        BookModel bm = BookModelAdapter.loadTemplate(temFile);
        BookModelAdapter.addEmptyForm(bm, 0);
        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
        FormModel fm = (FormModel) bm.forms.get(0);

        // === ELOLAP - Azonositok ===
        set(ds, "010001B001A", "60187590-1-33");     // Adoszam
        set(ds, "010001B004A", "8413921635");         // Adoazonosito jel
        System.out.println("   Elolap: OK");

        // === FOLAP (E) - Szemelyi adatok ===
        set(ds, "0A0001E001A", "8413921635");         // Adoazonosito jel
        set(ds, "0A0001E002A", "037098902");          // TAJ szam
        set(ds, "0A0001E003A", "HU");                 // Allampolgarsag
        set(ds, "0A0001E005A", "2030");               // Iranyitoszam
        set(ds, "0A0001E006A", "Érd");                // Telepules
        set(ds, "0A0001E007A", "Tusnádi");            // Kozterulet neve
        set(ds, "0A0001E008A", "1");                  // Kozterulet jellege (1=utca)
        set(ds, "0A0001E009A", "32");                 // Hazszam
        set(ds, "0A0001E011A", "1980.04.30");         // Szuletesi datum
        set(ds, "0A0001E019A", "Madarassy");          // Vezeteknev
        set(ds, "0A0001E021A", "László Tibor");       // Keresztnev
        set(ds, "0A0001E022A", "Madarassy");          // Szuletesi vezeteknev
        set(ds, "0A0001E023A", "László Tibor");       // Szuletesi keresztnev
        set(ds, "0A0001E024A", "Fehér Zsuzsanna");    // Anyja neve
        set(ds, "0A0001E025A", "Budapest");            // Szuletesi hely
        System.out.println("   Folap E: OK");

        // === FOLAP (F) - Bevallas jellege ===
        set(ds, "0A0001F026A", "H");                  // H=helyes bevallas
        System.out.println("   Folap F: OK");

        // === FOLAP (G) - Bankszamla ===
        set(ds, "0A0001G003A", "10404247-91529390-01540000");
        set(ds, "0A0001G007A", "2025");               // Koltsegvetesi ev
        System.out.println("   Folap G: OK");

        // === FOLAP (H) - Kapcsolat ===
        set(ds, "0A0001H003A", "204584812");          // Telefonszam
        System.out.println("   Folap H: OK");

        // === A LAP - Osszevonas ala eso jovedelmek ===
        // Fejlec azonositok az A lapon
        set(ds, "0B0001B003A", "60187590-1-33");      // A lap: adoszam
        set(ds, "0B0001B006A", "8413921635");          // A lap: adoazonosito

        // 1. sor "d" oszlop: Munkaviszonybol szarmazo berjovedelem
        // 20 000 000 Ft brutto eves ber
        set(ds, "0B0001C0001DA", "20000000");
        System.out.println("   A lap 1. sor: 20 000 000 Ft berjovedelem");

        // === B LAP - Adoalap megallpitas, kedvezmenyek ===
        // A B lap nagy resze readonly/szamitott mezo
        // 65. sor "b" oszlop: A munkaltato altal levont adoelőleg
        set(ds, "0D0001C0065BA", "1000000");
        System.out.println("   C lap 65. sor: 1 000 000 Ft levont adoeloleg");

        // === 03 LAP - Rendelkezo nyilatkozatok ===
        // 132. sor: Onkentes egeszsegpenztar befizetesek utani kedvezmeny
        // A kedvezmeny: befizetett osszeg 20%-a, max 150 000 Ft
        // Ha 150 000 Ft-ot fizetett be, a kedvezmeny: 150000 * 20% = 30 000 Ft
        // De a user 150 000 Ft kedvezmenyt irt -> az a befizetett osszeg 750 000 Ft-nak felel meg
        // Valoszinuleg a user a befizetett osszeget ertette
        // A 03-as lapon a 132. sor "b" oszlopaba a kedvezmeny osszege kerul (max 150000)
        // Keressuk a 03-as lap mezoit
        PageModel lap03 = null;
        for (int i = 0; i < fm.pages.size(); i++) {
            PageModel pm = (PageModel) fm.pages.get(i);
            if ("03".equals(pm.name)) { lap03 = pm; break; }
        }
        if (lap03 != null && lap03.y_sorted_df != null) {
            // 132. sor "b": Egeszsegpenztar kedvezmeny
            // Befizetett: 150 000 Ft -> kedvezmeny: 150000 * 20% = 30 000 Ft
            set(ds, "0GXXXXC0132BA", "30000");
            System.out.println("   03 lap 132. sor: 30 000 Ft egeszsegpenztar kedvezmeny (150000 * 20%)");
        }

        // === MENTES ===
        System.out.println("\n   Mentes (SimpleXmlSaver)...");
        String outPath = ROOT + "/2553_madarassy.xml";
        SimpleXmlSaver.save(bm, outPath);
        System.out.println("   SimpleXmlSaver OK: " + new File(outPath).length() + " bytes");

        System.out.println("   Mentes (EnykXmlSaver)...");
        try {
            hu.anyk.mcp.adapter.PropertyListInitializer.ensureInitialized(ROOT);
            hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver saver =
                new hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver(bm);
            String outPath2 = ROOT + "/2553_madarassy_enyk.xml";
            boolean saved = saver.save(outPath2, true);
            System.out.println("   EnykXmlSaver result=" + saved + " size=" + new File(outPath2).length());
        } catch (Exception e) {
            System.out.println("   EnykXmlSaver FAIL: " + e.getMessage());
            e.printStackTrace();
        }

        // === VALIDACIO ===
        System.out.println("\n   Validacio...");
        try {
            var checker = hu.piller.enykp.alogic.fileutil.DataChecker.getInstance();
            checker.superCheck(bm, true);
            System.out.println("   hasError=" + bm.hasError);
            if (bm.errorlist != null && bm.errorlist.size() > 0) {
                System.out.println("   Errors (" + bm.errorlist.size() + "):");
                for (int i = 0; i < Math.min(bm.errorlist.size(), 20); i++)
                    System.out.println("     " + bm.errorlist.get(i));
                if (bm.errorlist.size() > 20)
                    System.out.println("     ... +" + (bm.errorlist.size() - 20) + " more");
            }
        } catch (Exception e) {
            System.out.println("   Validation: " + e.getMessage());
        }

        // Kitoltott mezok
        int filled = 0;
        Enumeration<String> keys = fm.fids.keys();
        while (keys.hasMoreElements()) {
            String fid = keys.nextElement();
            String val = ds.get(new Object[]{0, fid});
            if (val != null && !val.isEmpty()) filled++;
        }
        System.out.println("   Kitoltott mezok: " + filled);

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
}
