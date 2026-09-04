package hu.anyk.mcp.adapter;

import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.gui.model.DataFieldModel;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.piller.enykp.datastore.Elem;
import hu.piller.enykp.alogic.metainfo.MetaInfo;
import hu.piller.enykp.alogic.metainfo.MetaStore;
import hu.piller.enykp.alogic.calculator.CalculatorManager;
import hu.piller.enykp.alogic.calculator.lookup.LookupListHandler;
import hu.piller.enykp.alogic.templateutils.FieldsGroups;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Az eredeti abevjava.jar BookModel osztalyt hasznalja.
 * Elofeltetel: PropertyListInitializer.ensureInitialized() lefutott
 * (prop.sys.root a valos ANYK telepitesre mutat, eroforrasok/ JAR-okkal,
 * MainFrame.role beallitva). Igy az eredeti BookModel(File) konstruktor
 * lefuttatja a makeempty()-t, ami inicializalja a Calculator-t, MetaInfo-t stb.
 */
public class BookModelAdapter {

    /**
     * Betolt egy sablont az EREDETI BookModel(File, silent) konstruktorral.
     * Ez inicializalja a CachedCollection-t, Calculator-t, MetaInfo-t.
     */
    public static BookModel loadTemplate(File templateFile) throws Exception {
        BookModel bm = new BookModel(templateFile, true); // silent = true
        if (bm.hasError) {
            throw new Exception("Template loading failed: " +
                (bm.errormsg != null ? bm.errormsg : "unknown error"));
        }
        if (bm.forms == null || bm.forms.isEmpty()) {
            throw new Exception("Template loading failed: no forms found");
        }
        return bm;
    }

    /**
     * Letrehoz egy ures nyomtatvany-peldanyt az EREDETI addForm() metodussal.
     * Ez a DefaultMultiFormViewer.buid() logikat koveti: a fo urlapot adja hozza.
     * Az addForm() futtatja a Calculator eventFired-et es a betoltesi szamitasokat.
     */
    public static void addEmptyForm(BookModel bm) {
        if (bm.cc != null && bm.cc.size() > 0) return; // mar van peldany

        FormModel mainForm;
        if (bm.size() == 1) {
            mainForm = bm.get(0);
        } else {
            mainForm = bm.get_main_formmodel();
            if (mainForm == null) mainForm = bm.get(0);
        }
        bm.addForm(mainForm);
    }

    /** Visszafele-kompatibilis: index parameteres valtozat. */
    public static void addEmptyForm(BookModel bm, int formIndex) {
        if (bm.cc != null && bm.cc.size() > 0) return;
        bm.addForm(bm.get(formIndex));
    }

    /**
     * Hozzaad egy tovabbi dokumentum-peldanyt (pl. kotegelt fedolap "25HIPAKM"),
     * ugyanugy mint a GUI multi-toolbar "uj lap" gombja (DefaultMultiFormViewer).
     * Kotegelt nyomtatvanyoknal a nem-fo dokumentumok igy kerulnek be, es igy
     * indul be a fo-adat (adoszam/nev) propagacio a fejleceikbe.
     * @return az uj peldany index a cc-ben, vagy -1 ha nem sikerult
     */
    public static int addDocument(BookModel bm, String formTypeId) throws Exception {
        FormModel fm = bm.get(formTypeId);
        if (fm == null) {
            throw new Exception("Ismeretlen dokumentumtipus: " + formTypeId);
        }
        int idx = bm.getIndex(fm);
        if (idx >= 0 && bm.maxcreation[idx] <= bm.created[idx]) {
            throw new Exception("Ebbol a dokumentumbol mar nem hozhato letre tobb: " + formTypeId);
        }
        bm.addForm(fm);
        return bm.cc.size() - 1;
    }

    /** A nyomtatvanyban elerheto dokumentumtipusok (fo + tovabbi lapok). */
    public static java.util.List<java.util.Map<String, Object>> listDocumentTypes(BookModel bm) {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        if (bm.forms == null) return list;
        for (int i = 0; i < bm.forms.size(); i++) {
            FormModel fm = (FormModel) bm.forms.get(i);
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", fm.id);
            m.put("name", fm.name);
            m.put("isMain", fm.id.equals(bm.main_document_id));
            m.put("maxCreation", i < bm.maxcreation.length ? bm.maxcreation[i] : 1);
            list.add(m);
        }
        return list;
    }

    /**
     * Gyors, fejlec-only betoltes listazashoz (nem futtatja a Calculator build-et).
     * A getHeadData() onlyhead=true modban parse-olja csak a docinfo/head reszt.
     */
    public static BookModel loadHead(File templateFile) throws Exception {
        BookModel bm = new BookModel();
        bm.silent = true;
        bm.getHeadData(templateFile);
        return bm;
    }

    public static GUI_Datastore getActiveDataStore(BookModel bm) {
        if (bm.cc == null || bm.cc.size() == 0) return null;
        Object active = bm.cc.getActiveObject();
        if (active instanceof Elem elem && elem.getRef() instanceof GUI_Datastore gds) {
            return gds;
        }
        Object first = bm.cc.get(0);
        if (first instanceof Elem elem && elem.getRef() instanceof GUI_Datastore gds) {
            return gds;
        }
        return null;
    }

    /** Az aktiv nyomtatvany-peldany tipusa (form id, pl. "25HIPAKA"). */
    public static String getActiveFormId(BookModel bm) {
        if (bm.cc == null || bm.cc.size() == 0) return null;
        Object active = bm.cc.getActiveObject();
        if (active instanceof Elem elem) return elem.getType();
        Object first = bm.cc.get(0);
        if (first instanceof Elem elem) return elem.getType();
        return null;
    }

    /**
     * Beallit egy mezot ES lefuttatja a mezo-szintu kalkulaciokat, ugyanugy mint
     * ahogy az ANYK GUI teszi minden mezoszerkesztes utan (PageViewer):
     *   1. ds.set(...)                              - ertek beallitasa
     *   2. calc_field(formId, fieldId, idx, key)    - fuggo mezok (pl. cim -> display mezo)
     *   3. calcReszbizonylatFuggosegek(formId,fid)  - fõlap azonositok propagalasa az allapokra
     *
     * E nelkul csak a nyers mezo kerul be, a szamitott/propagalt mezok nem
     * (ezert volt korabban sokkal kevesebb mezo a mentett fajlban).
     */
    /** Visszafele-kompatibilis wrapper: az AKTIV peldanyba ir. */
    public static void setFieldWithCalc(BookModel bm, GUI_Datastore dsHint,
                                        int pageIndex, String fieldId, String value) {
        setFieldOnActive(bm, pageIndex, fieldId, value);
    }

    /** Visszafele-kompatibilis wrapper: az AKTIV peldanyba ir. */
    public static void setFieldWithCalc(BookModel bm, int pageIndex, String fieldId, String value) {
        setFieldOnActive(bm, pageIndex, fieldId, value);
    }

    /**
     * Aktivva teszi az adott TIPUSU dokumentum-peldanyt (mint a GUI multi-toolbar
     * combobox valasztasa: cc.setActiveObject + setCalcelemindex). Ha tobb peldany
     * van ugyanabbol a tipusbol, az elsot valasztja.
     * @return a kivalasztott cc index, vagy -1 ha nincs ilyen tipusu peldany
     */
    public static int setActiveDocument(BookModel bm, String formTypeId) {
        if (bm.cc == null) return -1;
        for (int i = 0; i < bm.cc.size(); i++) {
            Object o = bm.cc.get(i);
            if (o instanceof Elem e && e.getType().equals(formTypeId)) {
                bm.cc.setActiveObject(e);
                bm.setCalcelemindex(i);
                hu.anyk.mcp.McpLog.note("setActiveDocument(" + formTypeId + ") -> idx=" + i);
                return i;
            }
        }
        hu.anyk.mcp.McpLog.note("setActiveDocument(" + formTypeId + ") -> NEM TALALT peldany");
        return -1;
    }

    /** Az aktiv dokumentum-peldany cc indexe. */
    public static int getActiveIndex(BookModel bm) {
        if (bm.cc == null) return -1;
        Object active = bm.cc.getActiveObject();
        if (active instanceof Elem e) return bm.cc.getIndex(e);
        return -1;
    }

    /**
     * Beallit egy mezot az AKTIV dokumentum-peldanyba, es lefuttatja a mezo-szintu
     * kalkulaciokat - PONTOSAN ugy, ahogy az ANYK GUI teszi. A GUI-ban a mezot
     * (aktiv peldany + fid) par cimzi: eloszor kivalasztod a peldanyt (combobox),
     * utana a fid abban a peldanyban ertelmezodik.
     *
     * Kotegelt nyomtatvanynal a fid NEM globalisan egyedi (pl. 0A0001C001A az
     * A-nal 'Adoszam', az M-nel 'onkormanyzat neve'), ezert TILOS a fid-bol
     * kitalalni a peldanyt. A hivo felelossege beallitani az aktiv dokumentumot
     * (setActiveDocument), mint a GUI-ban.
     *
     * @return true ha a mezo letezik az aktiv peldany form-jaban es beallitottuk;
     *         false ha a fid nincs az aktiv form fids-eben (nem irunk arva kodot).
     */
    public static boolean setFieldOnActive(BookModel bm, int pageIndex, String fieldId, String value) {
        int idx = getActiveIndex(bm);
        if (idx < 0) return false;
        Elem elem = (Elem) bm.cc.get(idx);
        String formId = elem.getType();

        FormModel fm = bm.get(formId);
        if (fm == null || fm.fids == null || fm.fids.get(fieldId) == null) {
            // A mezo nem tartozik az aktiv peldany form-jahoz -> nem irunk arva kodot
            hu.anyk.mcp.McpLog.set("setFieldOnActive", formId, idx, fieldId, value, false);
            return false;
        }

        GUI_Datastore ds = (GUI_Datastore) elem.getRef();
        var cm = CalculatorManager.getInstance();
        String key = pageIndex + "_" + fieldId;

        // Matrix/lookup-kotott mezo (pl. onkormanyzat neve): a beirt szoveget fel kell
        // oldani a pontos hivatalos listaelemre, kulonben a mezo ervenytelen marad es a
        // csoport-fuggo oszlopok sem toltodnek ki - pontosan ugy, mint az ANYK GUI comboja.
        MatrixResolution mr = resolveMatrixValue(formId, fieldId, pageIndex, value);
        if (mr != null) {
            ds.set(new Object[]{Integer.valueOf(pageIndex), fieldId}, mr.canonical);
            DataFieldModel dfm = (DataFieldModel) fm.fids.get(fieldId);
            try {
                cm.FillGroupFields(formId, ds, dfm, mr.recordIndex, pageIndex);
            } catch (Exception ignored) {
            }
            if (!mr.canonical.equals(value)) {
                hu.anyk.mcp.McpLog.note("matrix feloldas '" + value + "' -> '" + mr.canonical
                    + "' (idx=" + mr.recordIndex + ")");
            }
            hu.anyk.mcp.McpLog.set("setFieldOnActive", formId, idx, fieldId, mr.canonical, true);
            return true;
        }

        ds.set(new Object[]{Integer.valueOf(pageIndex), fieldId}, value);
        try {
            ds.inkihatas = true;
            cm.calc_field(formId, fieldId, pageIndex, key);
        } catch (Exception ignored) {
        } finally {
            ds.inkihatas = false;
        }
        try {
            ds.inkihatas = true;
            cm.calcReszbizonylatFuggosegek(formId, fieldId);
        } catch (Exception ignored) {
        } finally {
            ds.inkihatas = false;
        }
        // Diagnosztika: a calc UTAN visszaolvassuk - ha nem egyezik, a kalkulacio felulirta/torolte
        String after = ds.get(new Object[]{Integer.valueOf(pageIndex), fieldId});
        hu.anyk.mcp.McpLog.set("setFieldOnActive", formId, idx, fieldId, value, true);
        if (after == null ? value != null : !after.equals(value)) {
            hu.anyk.mcp.McpLog.note("WARN calc utan az ertek megvaltozott: '" + value + "' -> '" + after + "'");
        }
        return true;
    }

    /** Egy matrix/lookup mezo feloldott erteke + a matrix sor indexe. */
    private static final class MatrixResolution {
        final String canonical;
        final int recordIndex;
        MatrixResolution(String canonical, int recordIndex) {
            this.canonical = canonical;
            this.recordIndex = recordIndex;
        }
    }

    /**
     * Ha a mezo matrix/lookup-kotott (field_group_id + matrix_id a META-ban, pl. az
     * onkormanyzat neve mezo a 25HIPAKM lapon), feloldja a felhasznalo altal beirt
     * szoveget a pontos hivatalos listaelemre - ugy, mint az ANYK GUI comboja.
     * A matrix nevei kotott formaban vannak (pl. "ERD MEGYEI JOGU VAROS ONKORMANYZATA"),
     * ezert a szabad szoveges "Erd" nem egyezne, es a mezo ervenytelen maradna.
     *
     * Egyezteto strategia (kis/nagybetu-fuggetlen, trimmelt): 1) pontos, 2) prefix,
     * 3) tartalmaz. Ha egyetlen talalat van, azt hasznalja. Ha nincs talalat vagy
     * tobbertelmu, IllegalArgumentException-t dob a jelolt-listaval, hogy a hivo AI
     * a helyeset valaszthassa.
     *
     * @return a feloldott ertek + sorindex; vagy null ha a mezo NEM matrix-kotott
     *         (ilyenkor a hivo a szokasos uton allitja be)
     */
    private static MatrixResolution resolveMatrixValue(String formId, String fieldId,
                                                        int pageIndex, String value) {
        MetaStore ms = MetaInfo.getInstance().getMetaStore(formId);
        if (ms == null) return null;
        Map meta = ms.getFieldMetas(fieldId);
        if (meta == null) return null;
        Object groupId = meta.get(FieldsGroups.META_GROUP_ID);
        Object matrixId = meta.get(FieldsGroups.META_MATRIX_ID);
        if (groupId == null || String.valueOf(groupId).isEmpty()
            || matrixId == null || String.valueOf(matrixId).isEmpty()) {
            return null; // nem matrix-kotott mezo
        }
        Object colObj = meta.get(FieldsGroups.META_MATRIX_FIELD_COL);
        String col = colObj == null ? "1" : String.valueOf(colObj);

        List<String> names;
        try {
            names = LookupListHandler.getInstance()
                .getLookupListProvider(formId, fieldId)
                .getTableView(pageIndex, col);
        } catch (Exception e) {
            return null; // ha nem tudjuk lekerni a listat, hagyjuk a szokasos utat
        }
        if (names == null || names.isEmpty()) return null;

        String needle = value == null ? "" : value.trim();
        String needleLc = needle.toLowerCase();

        // 1) pontos (case-insensitive)
        int exact = -1;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i) != null && names.get(i).trim().equalsIgnoreCase(needle)) { exact = i; break; }
        }
        if (exact >= 0) return new MatrixResolution(names.get(exact), exact);

        // 2) prefix
        List<Integer> prefixHits = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (n != null && n.trim().toLowerCase().startsWith(needleLc)) prefixHits.add(i);
        }
        if (prefixHits.size() == 1) {
            int i = prefixHits.get(0);
            return new MatrixResolution(names.get(i), i);
        }

        // 3) tartalmaz
        List<Integer> containsHits = new ArrayList<>();
        if (prefixHits.isEmpty()) {
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i);
                if (n != null && n.toLowerCase().contains(needleLc)) containsHits.add(i);
            }
        }
        List<Integer> hits = !prefixHits.isEmpty() ? prefixHits : containsHits;

        if (hits.isEmpty()) {
            throw new IllegalArgumentException("A(z) '" + value + "' ertek nem talalhato a(z) '"
                + matrixId + "' listaban (" + names.size() + " elem). Add meg a pontos listaelemet.");
        }
        // tobbertelmu -> jeloltek visszaadasa (max 15)
        StringBuilder sb = new StringBuilder();
        sb.append("A(z) '").append(value).append("' tobb listaelemre is illik (")
          .append(hits.size()).append("). Valaszd a pontosat: ");
        for (int j = 0; j < hits.size() && j < 15; j++) {
            if (j > 0) sb.append(" | ");
            sb.append('\'').append(names.get(hits.get(j))).append('\'');
        }
        if (hits.size() > 15) sb.append(" | ...");
        throw new IllegalArgumentException(sb.toString());
    }
}
