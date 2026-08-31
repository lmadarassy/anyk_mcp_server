package hu.anyk.mcp.adapter;

import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.datastore.GUI_Datastore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SuppressWarnings("unchecked")
public class SimpleXmlSaver {

    private static final String XMLNS = "http://www.apeh.hu/abev/nyomtatvanyok/2005/01";
    private static final String PROGRAM_VERSION = "v.3.49.0";

    public static String generateFilename(BookModel bm, GUI_Datastore ds) {
        String formId = bm.id != null ? bm.id : "unknown";
        String adoazonosito = findValue(ds, "adoazonosito",
            "010001B004A", "0A0001E001A", "0A0001C002A");
        String nev = findPersonName(ds);
        long rnd = System.currentTimeMillis();
        String nevSafe = nev.replace(" ", "_");
        return formId + "_" + adoazonosito + "_" + nevSafe + "_" + rnd + ".frm.enyk";
    }

    public static boolean save(BookModel bm, String outputPath) throws Exception {
        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
        if (ds == null) throw new Exception("No active datastore");

        FormModel fm = (FormModel) bm.forms.get(0);
        String formId = bm.id != null ? bm.id : "";
        String ver = bm.docinfo != null ? (String) bm.docinfo.get("ver") : "1.0";
        String org = bm.docinfo != null ? (String) bm.docinfo.get("org") : "NAV";
        String templateName = org + "_" + formId + "_" + ver.replace(".", "_");

        String personName = findPersonName(ds);
        String adoazonosito = findValue(ds, "adoazonosito",
            "010001B004A", "0A0001E001A", "0A0001C002A", "0A0001C002A");
        String adoszam = findValue(ds, "adoszam",
            "010001B001A", "0A0001C001A");
        String tol = findValue(ds, "tol", "0A0001D001A");
        String ig = findValue(ds, "ig", "0A0001D002A");
        if (tol != null) tol = tol.replace("-", "").replace(".", "");
        if (ig != null) ig = ig.replace("-", "").replace(".", "");

        String saved = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<file>\n");

        sb.append("  <head filetype=\"zn1810\">\n");
        sb.append("    <type>single</type>\n");
        sb.append("    <saved>").append(saved).append("</saved>\n");
        sb.append("    <docinfo");
        sb.append(" name=\"").append(esc(formId)).append("\"");
        sb.append(" id=\"").append(esc(formId)).append("\"");
        sb.append(" count=\"1\"");
        sb.append(" note=\"\"");
        sb.append(" ver=\"").append(esc(PROGRAM_VERSION)).append("\"");
        sb.append(" org=\"").append(esc(org)).append("\"");
        sb.append(" templatever=\"").append(esc(ver)).append("\"");
        sb.append(" tax_number=\"").append(esc(adoszam)).append("\"");
        sb.append(" from_date=\"").append(esc(tol)).append("\"");
        sb.append(" to_date=\"").append(esc(ig)).append("\"");
        sb.append(" person_name=\"").append(esc(personName)).append("\"");
        sb.append(" account_name=\"").append(esc(adoazonosito)).append("\"");
        sb.append(" calculated=\"true\"");
        sb.append(" seq=\"1\"");
        sb.append(" krfilename=\"\"");
        sb.append(" avdh_cst=\"\"");
        sb.append(" />\n");
        sb.append("  </head>\n");

        sb.append("  <nyomtatvanyok xmlns=\"").append(XMLNS).append("\"");
        sb.append(" template=\"").append(esc(templateName)).append("\"");
        sb.append(" name=\"").append(esc(formId)).append("\"");
        sb.append(" id=\"").append(esc(formId)).append("\"");
        sb.append(">\n");

        sb.append("    <abev>\n");
        sb.append("      <hibakszama>-1</hibakszama>\n");
        sb.append("      <hash>                                        </hash>\n");
        sb.append("      <programverzio>").append(esc(PROGRAM_VERSION)).append("</programverzio>\n");
        sb.append("    </abev>\n");

        sb.append("    <nyomtatvany sn=\"0\">\n");
        sb.append("      <nyomtatvanyinformacio>\n");
        sb.append("        <nyomtatvanyazonosito>").append(esc(formId)).append("</nyomtatvanyazonosito>\n");
        sb.append("        <nyomtatvanyverzio>").append(esc(ver)).append("</nyomtatvanyverzio>\n");
        sb.append("        <adozo>\n");
        sb.append("          <nev>").append(esc(personName)).append("</nev>\n");
        if (adoazonosito != null && !adoazonosito.isEmpty())
            sb.append("          <adoazonosito>").append(esc(adoazonosito)).append("</adoazonosito>\n");
        if (adoszam != null && !adoszam.isEmpty())
            sb.append("          <adoszam>").append(esc(adoszam)).append("</adoszam>\n");
        sb.append("        </adozo>\n");
        sb.append("        <idoszak>\n");
        sb.append("          <tol>").append(esc(tol)).append("</tol>\n");
        sb.append("          <ig>").append(esc(ig)).append("</ig>\n");
        sb.append("        </idoszak>\n");
        sb.append("      </nyomtatvanyinformacio>\n");

        sb.append("      <mezok>\n");
        Enumeration<String> keys = fm.fids.keys();
        List<String> sortedKeys = new ArrayList<>();
        while (keys.hasMoreElements()) sortedKeys.add(keys.nextElement());
        Collections.sort(sortedKeys);

        for (String fid : sortedKeys) {
            String val = ds.get(new Object[]{0, fid});
            if (val != null && !val.isEmpty()) {
                if ("true".equals(val)) val = "X";
                if ("false".equals(val)) continue;
                sb.append("        <mezo eazon=\"0_").append(esc(fid)).append("\">")
                    .append(esc(val)).append("</mezo>\n");
            }
        }
        sb.append("      </mezok>\n");
        sb.append("    </nyomtatvany>\n");
        sb.append("  </nyomtatvanyok>\n");
        sb.append("</file>\n");

        String xml = sb.toString();
        try (OutputStream os = new FileOutputStream(outputPath)) {
            os.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        return true;
    }

    private static String findPersonName(GUI_Datastore ds) {
        String[][] namePairs = {
            {"0A0001E019A", "0A0001E021A"},
            {"0A0001C006A", "0A0001C007A"},
        };
        for (String[] pair : namePairs) {
            String v1 = safeGet(ds, pair[0]);
            String v2 = safeGet(ds, pair[1]);
            if (v1 != null || v2 != null) {
                String name = "";
                if (v1 != null) name += v1;
                if (v2 != null) name += " " + v2;
                return name.trim();
            }
        }
        return "";
    }

    private static String findValue(GUI_Datastore ds, String label, String... fids) {
        for (String fid : fids) {
            String val = safeGet(ds, fid);
            if (val != null && !val.isEmpty()) return val;
        }
        return "";
    }

    private static String safeGet(GUI_Datastore ds, String fid) {
        try {
            return ds.get(new Object[]{0, fid});
        } catch (Exception e) {
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
