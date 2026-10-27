// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Two explicit steps before any app feature; the same texts remain available for rereading. */
public final class SafetyActivity extends Activity {
    private boolean license;
    @Override public void onCreate(Bundle saved) {
        AppText.configure(getResources().getConfiguration().getLocales().get(0)); AppAppearance.apply(this); super.onCreate(saved);
        license=getIntent().getBooleanExtra("license",false);
        if(!SafetyConsent.ready(this)) SafetyConsent.disarm(this);
        render();
    }
    static String text(String name) throws IOException {
        try(InputStream in=SafetyActivity.class.getResourceAsStream("/safety/"+name)) {
            if(in==null) throw new IOException("Missing safety notice");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            return new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
        }
    }
    private String t(String de,String en) { return "de".equals(AppText.language())?de:en; }
    private void render() {
        LinearLayout body=LevelUi.screen(this);
        SafetyConsent.Stage stage=SafetyConsent.stage(this); boolean reading=stage==SafetyConsent.Stage.COMPLETE;
        try {
            if(license) {
                LevelUi.text(body,"GNU GPLv3",26); LevelUi.button(body,AppText.tr("Zurück"),v -> finish());
                LevelUi.text(body,text("gpl-3.0.txt"),14).setTextIsSelectable(true); return;
            }
            if(reading) {
                LevelUi.text(body,t("Sicherheit und Bedingungen","Safety and terms"),26);
                LevelUi.button(body,AppText.tr("Zurück"),v -> finish());
                LevelUi.text(body,text("warning-"+AppText.language()+".txt"),17).setTextIsSelectable(true);
                LevelUi.text(body,text("terms-"+AppText.language()+".txt"),17).setTextIsSelectable(true);
                licenseButton(body);return;
            }
            boolean warning=stage==SafetyConsent.Stage.WARNING;
            LevelUi.text(body,t(warning?"1 von 2 · Wichtiger Warnhinweis":"2 von 2 · Nutzungsbedingungen",warning?"1 of 2 · Important warning":"2 of 2 · Terms of use"),25).setTag("safety_heading");
            LevelUi.text(body,text((warning?"warning-":"terms-")+AppText.language()+".txt"),17).setTag("safety_text");
            if(!warning)licenseButton(body);
            CheckBox check=LevelUi.check(body,t(warning?"Ich habe den Warnhinweis gelesen und verstanden.":"Ich verstehe die Bedingungen und möchte fortfahren.",warning?"I have read and understood the warning.":"I understand the terms and wish to continue."),false);
            check.setTag("safety_check");check.setSaveEnabled(false);
            TextView error=LevelUi.text(body,"",14);error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
            Button next=LevelUi.button(body,t(warning?"Weiter":"Ich verstehe",warning?"Continue":"I understand"),v -> {
                if(!check.isChecked())return;
                if(!SafetyConsent.acknowledge(this,stage,true)) {error.setText(t("Bestätigung nicht gespeichert. Bitte erneut versuchen.","Acknowledgement was not saved. Please try again."));return;}
                if(SafetyConsent.ready(this)) {
                    CamApsConnection.ensure(this);
                    startActivity(new Intent(this,ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();
                }
                else render();
            });
            next.setTag("safety_next");next.setEnabled(false);check.setOnCheckedChangeListener((button,checked)->next.setEnabled(checked));
            LevelUi.button(body,t("App schließen","Close app"),v -> finishAndRemoveTask());
        } catch(IOException e) {
            LevelUi.text(body,t("Hinweise konnten nicht geladen werden. Die App bleibt gesperrt.","The notices could not be loaded. The app remains locked."),18);
            LevelUi.button(body,t("App schließen","Close app"),v -> finishAndRemoveTask());
        }
    }
    private void licenseButton(LinearLayout body) {
        LevelUi.button(body,t("GPLv3-Lizenz lesen","Read GPLv3 license"),v -> startActivity(new Intent(this,SafetyActivity.class).putExtra("license",true)));
    }
    @Override public void onBackPressed() {if(license || SafetyConsent.ready(this))super.onBackPressed();else finishAndRemoveTask();}
}
