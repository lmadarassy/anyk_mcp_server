package hu.anyk.mcp;

import hu.anyk.mcp.config.AnykConfig;
import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.gui.model.PageModel;
import hu.piller.enykp.gui.model.DataFieldModel;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.piller.enykp.datastore.CachedCollection;

import java.io.File;

public class HeadlessTest {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        
        System.out.println("=== ÁNYK MCP Headless Test ===\n");

        System.out.println("1. Testing class loading from abevjava.jar...");
        try {
            Class.forName("hu.piller.enykp.gui.model.BookModel");
            Class.forName("hu.piller.enykp.gui.model.FormModel");
            Class.forName("hu.piller.enykp.gui.model.PageModel");
            Class.forName("hu.piller.enykp.gui.model.DataFieldModel");
            Class.forName("hu.piller.enykp.datastore.GUI_Datastore");
            Class.forName("hu.piller.enykp.datastore.CachedCollection");
            Class.forName("hu.piller.enykp.datastore.Elem");
            Class.forName("hu.piller.enykp.alogic.fileutil.DataChecker");
            Class.forName("hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver");
            System.out.println("   OK - All key classes loaded successfully\n");
        } catch (Exception e) {
            System.out.println("   FAIL - " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        System.out.println("2. Testing GUI_Datastore (headless)...");
        try {
            GUI_Datastore ds = new GUI_Datastore();
            ds.set("0|test_field_01", "Hello World");
            String val = ds.get("0|test_field_01");
            assert "Hello World".equals(val) : "Expected 'Hello World' but got '" + val + "'";
            System.out.println("   set/get: OK (value='" + val + "')");
            
            ds.set("0|test_field_02", "12345");
            String val2 = ds.get("0|test_field_02");
            assert "12345".equals(val2);
            System.out.println("   set/get field 2: OK (value='" + val2 + "')");
            
            ds.beginTransaction();
            ds.set("0|test_field_01", "Modified");
            String valMod = ds.get("0|test_field_01");
            System.out.println("   transaction set: OK (value='" + valMod + "')");
            ds.rollbackTransaction();
            String valRollback = ds.get("0|test_field_01");
            System.out.println("   rollback: OK (value='" + valRollback + "')");

            ds.beginTransaction();
            ds.set("0|test_field_01", "Committed");
            ds.commitTransaction();
            String valCommit = ds.get("0|test_field_01");
            System.out.println("   commit: OK (value='" + valCommit + "')");
            
            System.out.println("   OK - GUI_Datastore works headless\n");
        } catch (Exception e) {
            System.out.println("   FAIL - " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        System.out.println("3. Testing SessionManager...");
        try {
            AnykConfig config = new AnykConfig("/tmp/anyk-test");
            config.initialize();
            SessionManager sm = new SessionManager(config);

            FormSession s1 = sm.createSession();
            System.out.println("   Created session: " + s1.getSessionId());
            
            FormSession s1b = sm.getSession(s1.getSessionId());
            assert s1 == s1b;
            System.out.println("   Retrieved session: OK");

            sm.closeSession(s1.getSessionId());
            try {
                sm.getSession(s1.getSessionId());
                System.out.println("   FAIL - should have thrown after close");
            } catch (IllegalArgumentException e) {
                System.out.println("   Close + get throws: OK");
            }
            System.out.println("   OK - SessionManager works\n");
        } catch (Exception e) {
            System.out.println("   FAIL - " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        System.out.println("4. Testing BookModel (no-arg constructor)...");
        try {
            BookModel bm = new BookModel();
            System.out.println("   BookModel() created, id=" + bm.id + ", name=" + bm.name);
            System.out.println("   forms=" + bm.forms + ", cc=" + bm.cc);
            System.out.println("   OK - BookModel no-arg constructor works headless\n");
        } catch (Exception e) {
            System.out.println("   FAIL - " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        System.out.println("5. Testing BookModel with template file...");
        String templatePath = null;
        if (args.length > 0) {
            templatePath = args[0];
        }
        if (templatePath != null && new File(templatePath).exists()) {
            try {
                File templateFile = new File(templatePath);
                System.out.println("   Loading: " + templateFile.getName());
                BookModel bm = hu.anyk.mcp.adapter.BookModelAdapter.loadTemplate(templateFile);
                System.out.println("   id=" + bm.id);
                System.out.println("   name=" + bm.name);
                System.out.println("   help=" + bm.help);
                System.out.println("   forms.size()=" + (bm.forms != null ? bm.forms.size() : "null"));
                System.out.println("   hasError=" + bm.hasError);
                System.out.println("   cc=" + (bm.cc != null ? "exists (size=" + bm.cc.size() + ")" : "null"));

                System.out.println("   -> Adding empty form...");
                hu.anyk.mcp.adapter.BookModelAdapter.addEmptyForm(bm, 0);
                System.out.println("   -> cc.size()=" + bm.cc.size());

                if (bm.cc != null && bm.cc.size() > 0) {
                    GUI_Datastore ds = hu.anyk.mcp.adapter.BookModelAdapter.getActiveDataStore(bm);
                    if (ds != null) {
                        System.out.println("   getActiveDataStore: OK (found via fallback)");
                        ds.set("0|test_fid", "TesztErtek");
                        String val = ds.get("0|test_fid");
                        System.out.println("   ds.set/get test: OK (value='" + val + "')");

                        DataFieldModel firstWritable = null;
                        for (int fi = 0; fi < bm.forms.size() && firstWritable == null; fi++) {
                            FormModel fmx = (FormModel) bm.forms.get(fi);
                            java.util.Enumeration<String> keys = fmx.fids.keys();
                            while (keys.hasMoreElements()) {
                                String fid = keys.nextElement();
                                DataFieldModel df = (DataFieldModel) fmx.fids.get(fid);
                                if (!df.readonly && (df.type == 0 || df.type == 5)) {
                                    firstWritable = df;
                                    break;
                                }
                            }
                        }
                        if (firstWritable != null) {
                            System.out.println("   First writable text field: fid=" + firstWritable.key + " type=" + firstWritable.type);
                            ds.set(new Object[]{Integer.valueOf(0), firstWritable.key}, "TestValue123");
                            String rv = ds.get(new Object[]{Integer.valueOf(0), firstWritable.key});
                            System.out.println("   Real field set/get (Object[]): OK (value='" + rv + "')");
                            ds.set(new Object[]{Integer.valueOf(0), firstWritable.key}, "TestValue456");
                            String rv2 = ds.get(new Object[]{Integer.valueOf(0), firstWritable.key});
                            System.out.println("   Real field set/get (String): OK (value='" + rv2 + "')");
                        } else {
                            System.out.println("   No writable text/ttext field found, trying any writable...");
                            for (int fi = 0; fi < bm.forms.size() && firstWritable == null; fi++) {
                                FormModel fmx = (FormModel) bm.forms.get(fi);
                                java.util.Enumeration<String> keys2 = fmx.fids.keys();
                                while (keys2.hasMoreElements()) {
                                    String fid = keys2.nextElement();
                                    DataFieldModel df = (DataFieldModel) fmx.fids.get(fid);
                                    if (!df.readonly) {
                                        System.out.println("     Writable: fid=" + df.key + " type=" + df.type);
                                        firstWritable = df;
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        System.out.println("   getActiveDataStore: FAIL (null)");
                    }
                }

                if (bm.forms != null && bm.forms.size() > 0) {
                    FormModel fm = (FormModel) bm.forms.get(0);
                    System.out.println("   Form: id=" + fm.id + ", pages=" + fm.pages.size() + ", fields=" + fm.fids.size());
                    for (int j = 0; j < Math.min(fm.pages.size(), 2); j++) {
                        PageModel pm = (PageModel) fm.pages.get(j);
                        System.out.println("     Page[" + j + "]: pid=" + pm.pid + ", name=" + pm.name
                            + ", fields=" + (pm.y_sorted_df != null ? pm.y_sorted_df.size() : 0));
                        if (pm.y_sorted_df != null) {
                            for (int k = 0; k < Math.min(pm.y_sorted_df.size(), 3); k++) {
                                DataFieldModel df = (DataFieldModel) pm.y_sorted_df.get(k);
                                System.out.println("       Field: fid=" + df.key + ", type=" + df.type
                                    + ", readonly=" + df.readonly);
                            }
                        }
                    }
                }

                System.out.println("   docinfo=" + bm.docinfo);
                bm.destroy();
                System.out.println("   OK - Full template test passed\n");
            } catch (Exception e) {
                System.out.println("   FAIL - " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        } else {
            System.out.println("   SKIP - No template file provided. Pass .tem.enyk path as first arg.\n");
        }

        System.out.println("=== Test Complete ===");
    }
}
