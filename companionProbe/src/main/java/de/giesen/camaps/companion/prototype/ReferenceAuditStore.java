// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class ReferenceAuditStore {
    private static final String PREFS="reference_audit_v1";
    static void record(Context context,ReferenceAudit.Match match,String exact,String shape) {
        try {
            SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String encoded=ReferenceAudit.append(prefs.getString("events",""),match,exact,shape);
            prefs.edit().putString("events",encoded).apply();
            String confirmations=context.getSharedPreferences(VectorReference.HISTORY_PREFS,Context.MODE_PRIVATE).getString("entries","");
            String report=ReferenceAudit.report(BuildConfig.VERSION_NAME,encoded,confirmations).replace("Datenbasis: keine Bilder, Werte oder Zeitstempel","Wiedererkannte Richtungen: "+ArrowCoverage.count(context)+"/7\nDatenbasis: keine Bilder, Werte oder Zeitstempel");
            Files.write(new File(context.getCacheDir(),"reference-audit.txt").toPath(),report.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) { /* Optional diagnostics never alter matching or alarm processing. */ }
    }
    static void clear(Context context) {
        context.getSharedPreferences(ArrowCoverage.PREFS,Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
        new File(context.getCacheDir(),"reference-audit.txt").delete();
    }
    private ReferenceAuditStore() {}
}
