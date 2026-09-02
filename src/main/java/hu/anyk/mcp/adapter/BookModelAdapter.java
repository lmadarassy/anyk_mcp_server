package hu.anyk.mcp.adapter;

import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.piller.enykp.datastore.Elem;

import java.io.File;

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
    public static void setFieldWithCalc(BookModel bm, GUI_Datastore dsHint,
                                        int pageIndex, String fieldId, String value) {
        setFieldWithCalc(bm, pageIndex, fieldId, value);
    }

    /**
     * Beallit egy mezot a HELYES dokumentum-peldanyba, es lefuttatja a mezo-szintu
     * kalkulaciokat (mint az ANYK GUI). Kotegelt nyomtatvanynal (tobb peldany a cc-ben)
     * automatikusan azt a peldanyt valasztja, amelynek a form-ja tartalmazza a mezot.
     *
     * Ez akadalyozza meg, hogy egy A-form mezo tevesen az aktiv M-peldany datastore-jaba
     * keruljon (arva kod), ami korabban a mentesi NPE-t es a rossz kitoltest okozta.
     */
    public static void setFieldWithCalc(BookModel bm, int pageIndex, String fieldId, String value) {
        int idx = resolveInstanceForField(bm, fieldId);
        if (idx < 0) {
            // Nem talaltuk egyik form fids-eben sem - beallitjuk az aktivba (visszaeses)
            GUI_Datastore ds = getActiveDataStore(bm);
            if (ds != null) ds.set(new Object[]{Integer.valueOf(pageIndex), fieldId}, value);
            return;
        }

        Elem elem = (Elem) bm.cc.get(idx);
        // A helyes peldanyt allitjuk aktivra (a calc a Calculator aktiv modelljere hat)
        bm.cc.setActiveObject(elem);
        bm.setCalcelemindex(idx);
        GUI_Datastore ds = (GUI_Datastore) elem.getRef();
        String formId = elem.getType();

        ds.set(new Object[]{Integer.valueOf(pageIndex), fieldId}, value);

        var cm = hu.piller.enykp.alogic.calculator.CalculatorManager.getInstance();
        String key = pageIndex + "_" + fieldId;
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
    }

    /**
     * Megkeresi, melyik dokumentum-peldany (cc index) form-jaba tartozik a mezo.
     * Ha az aktiv peldany form-ja tartalmazza, azt reszesiti elonyben (a GUI is az
     * aktualisan szerkesztett lapra ir). Kulonben az elso olyan peldany, amelynek
     * form-ja tartalmazza a mezot. -1 ha egyik sem.
     */
    public static int resolveInstanceForField(BookModel bm, String fieldId) {
        if (bm.cc == null || bm.cc.size() == 0) return -1;

        // 1. aktiv peldany elonyben, ha nala van a mezo
        Object active = bm.cc.getActiveObject();
        if (active instanceof Elem ae) {
            FormModel fm = bm.get(ae.getType());
            if (fm != null && fm.fids != null && fm.fids.get(fieldId) != null) {
                return bm.cc.getIndex(ae);
            }
        }
        // 2. barmely peldany, amelynek form-ja tartalmazza
        for (int i = 0; i < bm.cc.size(); i++) {
            Object o = bm.cc.get(i);
            if (o instanceof Elem e) {
                FormModel fm = bm.get(e.getType());
                if (fm != null && fm.fids != null && fm.fids.get(fieldId) != null) {
                    return i;
                }
            }
        }
        return -1;
    }
}
