package hu.anyk.mcp.tools;

import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.anyk.mcp.adapter.BookModelAdapter;

import java.awt.Rectangle;
import java.util.*;

@SuppressWarnings("unchecked")
public class StructureMapper {

    private static final String[] TYPE_NAMES = {
        "text", "check", "combo", "tatext", "date", "ttext", "tcombo", "ftext", "scrolltatext"
    };

    public static Map<String, Object> mapStructure(BookModel bm, String formTypeId, String pageId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> formsList = new ArrayList<>();

        if (bm.forms == null) {
            result.put("forms", formsList);
            return result;
        }

        for (int fi = 0; fi < bm.forms.size(); fi++) {
            FormModel fm = (FormModel) bm.forms.get(fi);
            if (formTypeId != null && !formTypeId.equals(fm.id)) continue;

            Map<String, Object> formMap = new LinkedHashMap<>();
            formMap.put("id", fm.id);
            formMap.put("name", fm.name);

            List<Map<String, Object>> pagesList = new ArrayList<>();
            if (fm.pages != null) {
                for (int pi = 0; pi < fm.pages.size(); pi++) {
                    PageModel pm = (PageModel) fm.pages.get(pi);
                    // A pageId lehet a pid (pl. "0") vagy a nev (pl. "Fõlap") is
                    if (pageId != null
                        && !pageId.equals(pm.pid)
                        && !pageId.equalsIgnoreCase(pm.name)) continue;

                    Map<String, Object> pageMap = new LinkedHashMap<>();
                    pageMap.put("id", pm.pid);
                    pageMap.put("name", pm.name);
                    pageMap.put("dynamic", pm.dynamic);
                    if (pm.dynamic) pageMap.put("maxPage", pm.maxpage);

                    List<Map<String, Object>> fieldsList = new ArrayList<>();
                    if (pm.y_sorted_df != null) {
                        Map<String, String> labelMap = buildLabelMap(pm, fm);
                        GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);

                        for (int di = 0; di < pm.y_sorted_df.size(); di++) {
                            DataFieldModel df = (DataFieldModel) pm.y_sorted_df.get(di);
                            Map<String, Object> fieldMap = mapField(df, fm, ds, labelMap);
                            fieldsList.add(fieldMap);
                        }
                    }
                    pageMap.put("fields", fieldsList);
                    pagesList.add(pageMap);
                }
            }
            formMap.put("pages", pagesList);
            formsList.add(formMap);
        }

        result.put("forms", formsList);
        return result;
    }

    public static Map<String, Object> mapField(DataFieldModel df, FormModel fm, GUI_Datastore ds, Map<String, String> labelMap) {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("fid", df.key);
        fieldMap.put("type", df.type >= 0 && df.type < TYPE_NAMES.length ? TYPE_NAMES[df.type] : "unknown");
        fieldMap.put("label", labelMap != null ? labelMap.getOrDefault(df.key, "") : "");
        fieldMap.put("readonly", df.readonly);

        if (df.features != null) {
            Object mask = df.features.get("mask");
            if (mask != null) fieldMap.put("mask", mask.toString());
            Object maxlen = df.features.get("len");
            if (maxlen != null) fieldMap.put("maxLength", maxlen.toString());
        }

        if (fm.irids != null && df.features != null) {
            Object iridRef = df.features.get("irids");
            if (iridRef != null) {
                String irule = (String) fm.irids.get(iridRef.toString());
                if (irule != null) fieldMap.put("inputRule", irule);
            }
        }

        if (df.type == 2 || df.type == 6) {
            if (df.features != null) {
                Object values = df.features.get("values");
                if (values != null) {
                    String[] options = values.toString().split(",");
                    fieldMap.put("options", List.of(options));
                }
            }
        }

        String currentValue = "";
        if (ds != null) {
            try {
                String val = ds.get(new Object[]{Integer.valueOf(0), df.key});
                if (val != null) currentValue = val;
            } catch (Exception ignored) {}
        }
        fieldMap.put("currentValue", currentValue);

        return fieldMap;
    }

    public static Map<String, String> buildLabelMap(PageModel pm, FormModel fm) {
        Map<String, String> labelMap = new HashMap<>();
        if (pm.z_sorted_vf == null || pm.y_sorted_df == null) return labelMap;

        for (int di = 0; di < pm.y_sorted_df.size(); di++) {
            DataFieldModel df = (DataFieldModel) pm.y_sorted_df.get(di);
            String bestLabel = "";
            int bestDist = Integer.MAX_VALUE;

            for (int vi = 0; vi < pm.z_sorted_vf.size(); vi++) {
                Object vfObj = pm.z_sorted_vf.get(vi);
                if (!(vfObj instanceof VisualFieldModel vf)) continue;
                if (vf.type != VisualFieldModel.TEXT) continue;
                if (vf.text == null || vf.text.isEmpty()) continue;

                Rectangle vfBounds = vf.getOriginalBounds();
                if (vfBounds == null) continue;

                int dx = df.x - (vfBounds.x + vfBounds.width);
                int dy = Math.abs(df.y - vfBounds.y);

                if (dx >= -5 && dx < 300 && dy < 15) {
                    int dist = Math.abs(dx) + dy;
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestLabel = vf.text;
                    }
                }

                int dxAbove = Math.abs(df.x - vfBounds.x);
                int dyAbove = df.y - (vfBounds.y + vfBounds.height);
                if (dxAbove < 50 && dyAbove >= 0 && dyAbove < 25) {
                    int dist = dxAbove + dyAbove;
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestLabel = vf.text;
                    }
                }
            }
            if (!bestLabel.isEmpty()) {
                labelMap.put(df.key, bestLabel.trim());
            }
        }
        return labelMap;
    }
}
