// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;

/** Five named settings slots kept in private app storage. */
public final class ProfilesActivity extends UiActivity {
    private LinearLayout list;
    private TextView status;
    private ProfileStore store;
    private ProfileStore.Snapshot snapshot;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if(!SafetyConsent.ready(this))return; store=new ProfileStore(getFilesDir()); LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück"),v -> finish()); LevelUi.text(body,AppText.tr("Profile"),28);
        LevelUi.text(body,AppText.tr("Fünf Speicherplätze für Deine Einstellungen."),16);
        LevelUi.help(body,AppText.tr("Was wird gespeichert?"),AppText.tr("Alarmstufen, Erholungsregeln, Lückenwarnung, Ausgabewege, Töne, Vibrationsmuster, Lautstärken, Texte, Darstellung und AOD-Einstellungen. Beim Laden wird die Live-Ausgabe ausgeschaltet. Aktuelle Werte, Pfeilzuordnungen, laufende Snooze-Fristen und Systemfreigaben gehören nicht zum Profil."));
        status=LevelUi.text(body,"",15); status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); body.addView(list);
    }
    @Override public void onResume() { super.onResume(); if(!SafetyConsent.ready(this))return; render(); }
    private void render() {
        list.removeAllViews();
        try { snapshot=store.load(); }
        catch(IOException e) { snapshot=null; status.setText(AppText.tr("Profile nicht lesbar; vorhandene Datei bleibt erhalten.")); return; }
        for(int slot=0;slot<ProfileStore.SLOTS;slot++) {
            final int index=slot; final long revision=snapshot.revision; final ProfileStore.Entry entry=snapshot.slots.get(slot);
            LinearLayout card=DashboardUi.card(this,list); card.setTag("profile_slot_"+slot);
            LevelUi.text(card,(slot+1)+" · "+(entry==null?AppText.tr("Frei"):entry.name),20);
            if(entry==null) LevelUi.button(card,AppText.tr("Speichern"),v -> save(index,revision,null));
            else {
                LevelUi.button(card,AppText.tr("Laden"),v -> new AlertDialog.Builder(this).setTitle("„"+entry.name+"“ laden?")
                        .setMessage(AppText.tr("Einheit für Anzeige und Ansagen: ")+entry.settings.output.preferences.unitMode.title()+"\n\n"+AppText.tr("Ersetzt Deine aktuellen Einstellungen. Die Live-Ausgabe wird ausgeschaltet; danach bitte im Dashboard wieder einschalten. Laufende Snooze-Fristen bleiben erhalten, soweit die Alarmbedingung unverändert ist."))
                        .setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Laden"),(d,w) -> load(index,revision)).show());
                LinearLayout row=new LinearLayout(this); card.addView(row);
                Button replace=LevelUi.button(row,AppText.tr("Ersetzen"),v -> save(index,revision,entry.name));
                Button delete=LevelUi.button(row,AppText.tr("Löschen"),v -> new AlertDialog.Builder(this).setTitle(AppText.tr("Profil löschen?")).setMessage("„"+entry.name+AppText.tr("“ entfernen? Die aktuellen Einstellungen bleiben erhalten."))
                        .setNegativeButton(AppText.tr("Behalten"),null).setPositiveButton(AppText.tr("Löschen"),(d,w) -> { try { store.save(revision,index,null); status.setText(AppText.tr("Profil gelöscht.")); } catch(IOException e) { status.setText(e.getMessage()); } render(); }).show());
                replace.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1); right.leftMargin=LevelUi.dp(this,8); delete.setLayoutParams(right);
            }
        }
    }
    private void save(int slot,long revision,String previous) {
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint(AppText.tr("Profilname")); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)}); if(previous!=null) name.setText(previous);
        LinearLayout panel=new LinearLayout(this); int pad=LevelUi.dp(this,20); panel.setPadding(pad,0,pad,0); panel.addView(name,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(previous==null?AppText.tr("Aktuelle Einstellungen speichern"):AppText.tr("Profil ersetzen?"))
                .setMessage(previous==null?null:AppText.tr("Speichert die aktuellen Einstellungen in diesem Platz.")).setView(panel).setNegativeButton(AppText.tr("Abbrechen"),null).setPositiveButton(AppText.tr("Speichern"),null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { store.save(revision,slot,new ProfileStore.Entry(name.getText().toString(),ProfileController.capture(this))); dialog.dismiss(); status.setText(AppText.tr("Profil gespeichert.")); render(); }
            catch(IllegalArgumentException | IOException e) { name.setError(e.getMessage()); }
        })); dialog.show();
    }
    private void load(int slot,long revision) {
        try {
            ProfileStore.Snapshot current=store.load(); if(current.revision!=revision || current.slots.get(slot)==null) throw new IOException(AppText.tr("Profile wurden inzwischen geändert. Bitte erneut wählen."));
            ProfileController.apply(this,current.slots.get(slot).settings);
            Toast.makeText(this,AppText.tr("Profil geladen · Live-Ausgabe aus"),Toast.LENGTH_LONG).show(); finish();
        } catch(IOException | IllegalArgumentException e) { status.setText(e.getMessage()); render(); }
    }
}
