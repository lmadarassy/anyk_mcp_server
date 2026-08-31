package hu.anyk.mcp.adapter;

import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.datastore.CachedCollection;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.piller.enykp.datastore.Elem;

import java.io.File;

public class BookModelAdapter {

    @SuppressWarnings("unchecked")
    public static BookModel loadTemplate(File templateFile) throws Exception {
        BookModel bm = new BookModel();
        bm.silent = true;
        bm.load(templateFile);

        if (bm.forms == null || bm.forms.isEmpty()) {
            throw new Exception("Template loading failed: no forms found. " +
                (bm.errormsg != null ? bm.errormsg : ""));
        }

        bm.hasError = false;
        bm.errormsg = null;

        CachedCollection cc = new CachedCollection();
        cc.bm = bm;
        bm.cc = cc;

        int formCount = bm.forms.size();
        bm.maxcreation = new int[formCount];
        bm.created = new int[formCount];
        for (int i = 0; i < formCount; i++) {
            FormModel fm = (FormModel) bm.forms.get(i);
            bm.maxcreation[i] = fm.maxcreation;
            bm.created[i] = 0;
        }

        return bm;
    }

    @SuppressWarnings("unchecked")
    public static void addEmptyForm(BookModel bm, int formIndex) {
        FormModel fm = (FormModel) bm.forms.get(formIndex);

        bm.created[formIndex]++;

        GUI_Datastore ds = new GUI_Datastore();
        Elem elem = new Elem(ds, fm.id, fm.name);

        int[] pagecounts = new int[fm.pages != null ? fm.pages.size() : 0];
        for (int i = 0; i < pagecounts.length; i++) {
            pagecounts[i] = 1;
        }
        elem.getEtc().put("pagecounts", pagecounts);
        elem.getEtc().put("sn", bm.cc.getSequence());

        bm.cc.add(elem);
    }

    public static GUI_Datastore getActiveDataStore(BookModel bm) {
        if (bm.cc == null || bm.cc.size() == 0) return null;
        Object active = bm.cc.getActiveObject();
        if (active instanceof Elem elem) {
            Object ref = elem.getRef();
            if (ref instanceof GUI_Datastore gds) return gds;
        }
        Object first = bm.cc.get(0);
        if (first instanceof Elem elem) {
            Object ref = elem.getRef();
            if (ref instanceof GUI_Datastore gds) return gds;
        }
        return null;
    }
}
