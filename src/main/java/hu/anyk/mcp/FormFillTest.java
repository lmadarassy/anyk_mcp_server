package hu.anyk.mcp;

import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;

import java.io.File;
import java.util.*;

public class FormFillTest {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        String templatePath = args.length > 0 ? args[0] : "/tmp/opencode/test_template.tem.enyk";
        File templateFile = new File(templatePath);

        System.out.println("=== 2558 Form Structure Analysis ===\n");

        BookModel bm = BookModelAdapter.loadTemplate(templateFile);
        BookModelAdapter.addEmptyForm(bm, 0);
        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);

        FormModel fm = (FormModel) bm.forms.get(0);
        System.out.println("Form: " + fm.id + " / " + fm.name);
        System.out.println("Pages: " + fm.pages.size());
        System.out.println("Total fields: " + fm.fids.size());
        System.out.println("Help: " + bm.help);
        System.out.println("Docinfo: " + bm.docinfo);
        System.out.println();

        for (int p = 0; p < fm.pages.size(); p++) {
            PageModel pm = (PageModel) fm.pages.get(p);
            System.out.println("--- Page " + p + ": pid=" + pm.pid + " name=" + pm.name 
                + " dynamic=" + pm.dynamic + " fields=" + (pm.y_sorted_df != null ? pm.y_sorted_df.size() : 0) + " ---");

            if (pm.y_sorted_df == null) continue;

            Map<String, String> labelMap = buildLabelMap(pm);

            for (int f = 0; f < pm.y_sorted_df.size(); f++) {
                DataFieldModel df = (DataFieldModel) pm.y_sorted_df.get(f);
                String typeName = getTypeName(df.type);
                String label = labelMap.getOrDefault(df.key, "");
                String extra = "";
                if (df.features != null) {
                    Object mask = df.features.get("mask");
                    Object len = df.features.get("len");
                    Object values = df.features.get("values");
                    Object data_type = df.features.get("data_type");
                    if (mask != null) extra += " mask=" + mask;
                    if (len != null) extra += " len=" + len;
                    if (data_type != null) extra += " dt=" + data_type;
                    if (values != null) {
                        String vs = values.toString();
                        if (vs.length() > 80) vs = vs.substring(0, 80) + "...";
                        extra += " vals=" + vs;
                    }
                }
                System.out.printf("  %s %-20s %-6s ro=%-5s %s%s%n",
                    df.key, label.length() > 20 ? label.substring(0, 20) : label,
                    typeName, df.readonly, extra,
                    df.type == 1 ? " [checkbox]" : "");
            }
            System.out.println();
        }
    }

    static String getTypeName(int type) {
        return switch (type) {
            case 0 -> "text";
            case 1 -> "check";
            case 2 -> "combo";
            case 3 -> "tatext";
            case 4 -> "date";
            case 5 -> "ttext";
            case 6 -> "tcombo";
            case 7 -> "ftext";
            case 8 -> "scroll";
            default -> "t" + type;
        };
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> buildLabelMap(PageModel pm) {
        Map<String, String> map = new HashMap<>();
        if (pm.z_sorted_vf == null || pm.y_sorted_df == null) return map;

        for (int di = 0; di < pm.y_sorted_df.size(); di++) {
            DataFieldModel df = (DataFieldModel) pm.y_sorted_df.get(di);
            String best = "";
            int bestDist = Integer.MAX_VALUE;
            for (int vi = 0; vi < pm.z_sorted_vf.size(); vi++) {
                Object o = pm.z_sorted_vf.get(vi);
                if (!(o instanceof VisualFieldModel vf)) continue;
                if (vf.type != 0 || vf.text == null || vf.text.isEmpty()) continue;
                java.awt.Rectangle vb = vf.getOriginalBounds();
                if (vb == null) continue;
                int dx = df.x - (vb.x + vb.width);
                int dy = Math.abs(df.y - vb.y);
                if (dx >= -5 && dx < 300 && dy < 15) {
                    int d = Math.abs(dx) + dy;
                    if (d < bestDist) { bestDist = d; best = vf.text; }
                }
                int dxa = Math.abs(df.x - vb.x);
                int dya = df.y - (vb.y + vb.height);
                if (dxa < 100 && dya >= 0 && dya < 30) {
                    int d = dxa + dya;
                    if (d < bestDist) { bestDist = d; best = vf.text; }
                }
            }
            if (!best.isEmpty()) map.put(df.key, best.trim());
        }
        return map;
    }
}
