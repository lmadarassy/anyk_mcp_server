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
}
