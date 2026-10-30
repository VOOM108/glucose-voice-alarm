// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.app.*;
import android.content.Intent;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.util.*;

public final class AlarmLevelActivity extends UiActivity {
    private static final int PICK_TONE=40;
    private LevelStore store;
    private LevelStore.Snapshot snapshot;
    private String id,toneUri=AlarmLevel.DEFAULT_TONE;
    private AlarmEngine.Kind kind;
    private boolean existing;
    private boolean ready;
    private long expectedRevision;
    private int startMinute=-1,endMinute=-1;
    private EditText name,threshold,duration,repeat,snooze,speechTexts;
    private CheckBox enabled,tone,speech,allDay,vibration,recovery;
    private Spinner vibrationPattern;
    private Button previewVibration;
    private Button start,end,chooseTone,previewTone,previewSpeech,save;
    private TextView error,toneName;
    private ArrayList<String> initial;
    private PreviewAudio audio;
    private long speechVariant;
    private GlucoseUnit editingUnit;
    private String initialThreshold;
    private Double existingThreshold;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if(!SafetyConsent.ready(this))return; store=new LevelStore(getFilesDir());
        LinearLayout body=LevelUi.screen(this);
        LevelUi.button(body,AppText.tr("Zurück zur Liste"),v -> onBackPressed());
        try {
            snapshot=store.load(); existing=getIntent().hasExtra("id");
            id=existing?getIntent().getStringExtra("id"):saved!=null?saved.getString("draftId"):UUID.randomUUID().toString();
            AlarmLevel original=existing?snapshot.find(id):null;
            if (existing && original==null) throw new IOException("Missing level");
            kind=existing?original.rule.kind:AlarmEngine.Kind.valueOf(getIntent().getStringExtra("kind"));
            expectedRevision=saved!=null?saved.getLong("revision"):snapshot.revision;
            AlarmEngine.Rule r=original==null?null:original.rule;
            editingUnit=saved!=null && saved.containsKey("editing_unit")?GlucoseUnit.valueOf(saved.getString("editing_unit")):UnitPreferences.display(this);
            existingThreshold=r==null?null:r.threshold; initialThreshold=r==null?"":editingUnit.threshold(r.threshold,LevelForm.isRate(kind));
            if (r!=null && (!r.allDay || r.startMinute!=0 || r.endMinute!=1440)) { startMinute=r.startMinute; endMinute=r.endMinute; }
            toneUri=original==null?SoundDefaults.uri(this,kind):original.toneUri;
            LevelUi.text(body,existing?AppText.tr("Stufe bearbeiten"):AppText.tr("Neue Alarmstufe"),28);
            LevelUi.text(body,LevelForm.kindLabel(kind),22);
            LevelUi.text(body,AppText.tr("Speichern schaltet die Live-Ausgabe nicht ein."),14);
            name=LevelUi.field(body,AppText.tr("Name der Stufe"),original==null?LevelForm.kindLabel(kind):original.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
            enabled=LevelUi.check(body,AppText.tr("Alarmstufe einschalten"),r!=null && r.enabled);
            String limitLabel=LevelForm.isRate(kind)?(kind==AlarmEngine.Kind.FALLING?AppText.tr("Abnahme in "):AppText.tr("Anstieg in "))+editingUnit.label+AppText.tr(" pro Minute (positiv eingeben)"):AppText.tr("Grenzwert in ")+editingUnit.label;
            threshold=LevelUi.field(body,limitLabel,initialThreshold,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            threshold.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
            if (kind==AlarmEngine.Kind.PERSISTENT_HIGH) {
                duration=LevelUi.field(body,AppText.tr("Länger als … Minuten (xDrip-Regel)"),r==null?"":Integer.toString(r.durationMinutes),InputType.TYPE_CLASS_NUMBER);
                LevelUi.text(body,AppText.tr("Prüfung bei steigenden Werten. Gleichbleibende und fallende Phasen oberhalb der Grenze zählen zur verstrichenen Dauer mit."),14);
            }
            allDay=LevelUi.check(body,AppText.tr("Ganztägig"),r==null || r.allDay);
            start=LevelUi.button(body,"",v -> chooseTime(true)); end=LevelUi.button(body,"",v -> chooseTime(false));
            if (kind==AlarmEngine.Kind.PERSISTENT_HIGH) LevelUi.text(body,AppText.tr("Ein Zeitfenster begrenzt die spätere Ausgabe; die Dauerberechnung bleibt nach der xDrip-Regel."),14);
            LevelUi.text(body,AppText.tr("Ausgabe dieser Stufe"),21);
            tone=LevelUi.check(body,AppText.tr("Ton für diese Stufe"),r!=null && r.outputs.tone);
            toneName=LevelUi.text(body,"",14);
            LinearLayout soundRow=new LinearLayout(this); soundRow.setGravity(android.view.Gravity.CENTER_VERTICAL); soundRow.setBaselineAligned(false); body.addView(soundRow);
            chooseTone=LevelUi.button(soundRow,AppText.tr("Ton auswählen"),v -> chooseTone()); chooseTone.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            previewTone=LevelUi.button(soundRow,AppText.tr("Ton anhören"),v -> playTone());
            vibration=LevelUi.check(body,AppText.tr("Vibration für diese Stufe"),original!=null && original.vibrationPattern!=null);
            LinearLayout vibrationRow=new LinearLayout(this); vibrationRow.setGravity(android.view.Gravity.CENTER_VERTICAL); body.addView(vibrationRow);
            vibrationPattern=new Spinner(this); vibrationPattern.setContentDescription(AppText.tr("Vibrationsmuster"));
            String[] patterns=new String[VibrationCatalog.values().length]; for(int i=0;i<patterns.length;i++) patterns[i]=AppText.tr(VibrationCatalog.values()[i].label);
            vibrationPattern.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,patterns)); vibrationRow.addView(vibrationPattern,new LinearLayout.LayoutParams(0,-2,1));
            if(original!=null && original.vibrationPattern!=null) vibrationPattern.setSelection(VibrationCatalog.valueOf(original.vibrationPattern).ordinal());
            previewVibration=LevelUi.button(vibrationRow,AppText.tr("Vibration testen"),v -> audio.vibrate(selectedVibration()));
            speech=LevelUi.check(body,AppText.tr("Gesprochener Alarm für diese Stufe"),r!=null && r.outputs.speech);
            speechTexts=LevelUi.field(body,AppText.tr("Eigene Formulierungen (leer für Standardtext)"),original==null?"":android.text.TextUtils.join("\n",original.speechVariants),
                    InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            speechTexts.setMinLines(2); speechTexts.setMaxLines(6);
            speechTexts.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2003)});
            if(kind==AlarmEngine.Kind.PERSISTENT_HIGH) TokenButtons.add(body,speechTexts,"{wert}","{einheit}","{stufe}","{grenze}","{trend}","{dauer_minuten}");
            else TokenButtons.add(body,speechTexts,"{wert}","{einheit}","{stufe}","{grenze}","{trend}");
            LevelUi.help(body,AppText.tr("Textregeln"),AppText.tr("Bis zu vier Formulierungen, eine je Zeile, höchstens 500 Zeichen. Leer verwendet den Standard: ")+SpeechTemplate.defaultAlarm(kind,new OutputPreferenceStore(getFilesDir()).load().preferences.speechLanguage));
            LevelUi.text(body,AppText.tr("Anhören mit Testwert"),14);
            LinearLayout samples=new LinearLayout(this); samples.setGravity(android.view.Gravity.CENTER_VERTICAL); body.addView(samples);
            previewSpeech=LevelUi.button(samples,AppText.tr("Sprachtext mit Testdaten anhören"),v -> {
                try { OutputPreferences p=new OutputPreferenceStore(getFilesDir()).load().preferences; if (speech.isChecked() && audio.speak(SyntheticSpeech.alarm(kind,name.getText().toString(),speechTexts.getText().toString(),speechVariant,p.trendSpeech,p.speechLanguage,editingUnit))) speechVariant++; }
                catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
            });
            LevelUi.button(samples,AppText.tr("Vorschau stoppen"),v -> stopTone());
            if (LevelForm.hasRepeat(kind)) LevelUi.text(body,AppText.tr("Wiederholungen"),21);
            if(LevelForm.hasSnooze(kind)) { recovery=LevelUi.check(body,AppText.tr("Bei Erholung aussetzen"),original!=null && original.suppressRecoveryRepeats); LevelUi.text(body,kind==AlarmEngine.Kind.LOW?AppText.tr("Nur Wiederholungen bei steigendem Verlauf aussetzen. Der erste Alarm bleibt."):AppText.tr("Nur Wiederholungen bei fallendem Verlauf aussetzen. Der erste Alarm bleibt."),14); LevelUi.help(body,AppText.tr("Wann gilt Erholung?"),AppText.tr("Ein frischer eindeutiger CamAPS-Pfeil und die berechnete xDrip-Erholungsprüfung müssen übereinstimmen. Bei stabilem, fehlendem oder widersprüchlichem Trend wird nicht ausgesetzt. Snooze bleibt unverändert.")); }
            if (LevelForm.hasRepeat(kind)) repeat=LevelUi.field(body,AppText.tr("Wiederholung in Minuten"),r==null?"":Integer.toString(r.outputs.repeatMinutes),InputType.TYPE_CLASS_NUMBER);
            if (LevelForm.hasSnooze(kind)) snooze=LevelUi.field(body,AppText.tr("Snooze nach Quittierung in Minuten"),r==null?"":Integer.toString(r.outputs.snoozeMinutes),InputType.TYPE_CLASS_NUMBER);
            if (LevelForm.hasSnooze(kind)) LevelUi.help(body,AppText.tr("Wann beginnt ein neuer Alarm?"),AppText.tr("Ein frischer gültiger Wert unter der Hochgrenze bzw. über der Niedriggrenze beendet die automatische Wiederholung. Beim nächsten Grenzübertritt ist wieder ein erster Alarm möglich. Snooze und globale Pause bleiben bestehen. Eine Datenlücke setzt die Wiederholung nicht zurück."));
            if (LevelForm.isRate(kind)) {
                LevelUi.text(body,AppText.tr("Eigene Schwelle für die empfangenen Werte, unabhängig vom CamAPS-Pfeil und vom Abstand der Wertansagen. Vier Minutenpunkte und das letzte Intervall müssen passen. Ohne neue Werte kein Ratenalarm."),14);
                LevelUi.help(body,AppText.tr("Erneutes Auslösen"),AppText.tr("Ereignisregel nach xDrip: grundsätzlich einmal pro erkannter Phase. Nach Ende der Bedingung kann eine neue Phase erneut melden; bei durchgehendem Zustand ist technisch nach knapp sechs Stunden eine erneute Meldung möglich. Keine einstellbare Wiederholungs- oder Snoozezeit."));
            }
            else if (kind==AlarmEngine.Kind.PERSISTENT_HIGH) LevelUi.text(body,AppText.tr("Eigener Wiederholungsabstand nach xDrip; kein Stufen-Snooze in diesem Alarmweg."),14);
            error=LevelUi.text(body,"",15); error.setTextColor(AppAppearance.colors(this).error); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            audio=new PreviewAudio(this,message -> error.setText(message));
            save=LevelUi.button(body,AppText.tr("Stufe speichern"),v -> save());
            LevelUi.button(body,AppText.tr("Abbrechen"),v -> onBackPressed());
            initial=values();
            if (saved!=null) restore(saved);
            vibration.setOnCheckedChangeListener((v,checked) -> { stopTone(); updateControls(); });
            allDay.setOnCheckedChangeListener((v,checked) -> updateControls());
            tone.setOnCheckedChangeListener((v,checked) -> { if (!checked) stopTone(); updateControls(); });
            speech.setOnCheckedChangeListener((v,checked) -> { if (!checked) stopTone(); updateControls(); if (checked) audio.prepareSpeech(); });
            enabled.setOnCheckedChangeListener((v,checked) -> { if (!checked) stopTone(); });
            updateControls();
            if (speech.isChecked()) audio.prepareSpeech();
            ready=true;
            if (snapshot.revision!=expectedRevision) { error.setText(AppText.tr("Alarmstufen wurden inzwischen geändert. Bitte die Stufe aus der aktuellen Liste neu öffnen.")); save.setEnabled(false); }
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            ready=false; if (save!=null) save.setEnabled(false);
            LevelUi.text(body,AppText.tr("Diese Alarmstufe konnte nicht geöffnet werden. Gespeicherte Einstellungen bleiben unverändert. Bitte zur Liste zurückkehren."),16);
        }
    }
    private void chooseTime(boolean beginning) {
        int current=beginning?startMinute:endMinute;
        Calendar calendar=Calendar.getInstance();
        int hour=current>=0?(current/60)%24:calendar.get(Calendar.HOUR_OF_DAY);
        int minute=current>=0?current%60:calendar.get(Calendar.MINUTE);
        new TimePickerDialog(this,(picker,h,m) -> { if (beginning) startMinute=h*60+m; else endMinute=h*60+m; updateControls(); },hour,minute,true).show();
    }
    private String clock(int value) { return String.format(Locale.GERMANY,"%02d:%02d",value/60,value%60); }
    private void updateControls() {
        boolean spoken=speech.isChecked();
        try { OutputPreferences p=new OutputPreferenceStore(getFilesDir()).load().preferences; spoken&=p.alarmSpeechEnabled && p.speechVolumePercent>0; } catch (IOException e) { spoken=false; }
        boolean haptic=false;
        try { haptic=vibration.isChecked() && new OutputPreferenceStore(getFilesDir()).load().preferences.vibrationEnabled; } catch(IOException ignored) { }
        boolean optional=kind==AlarmEngine.Kind.PERSISTENT_HIGH || haptic;
        vibrationPattern.setEnabled(vibration.isChecked()); previewVibration.setEnabled(vibration.isChecked());
        if (!spoken && !optional && !tone.isChecked()) tone.setChecked(true);
        tone.setEnabled(spoken || optional); tone.setText(spoken || optional?AppText.tr("Ton für diese Stufe"):AppText.tr("Ton erforderlich: Sprache/Vibration aus"));
        start.setVisibility(allDay.isChecked()?View.GONE:View.VISIBLE); end.setVisibility(allDay.isChecked()?View.GONE:View.VISIBLE);
        start.setText(startMinute<0?AppText.tr("Beginn auswählen"):"Von "+clock(startMinute));
        end.setText(endMinute<0?AppText.tr("Ende auswählen"):"Bis "+clock(endMinute));
        chooseTone.setEnabled(tone.isChecked()); previewTone.setEnabled(tone.isChecked());
        speechTexts.setEnabled(speech.isChecked()); previewSpeech.setEnabled(speech.isChecked());
        TokenButtons.enabled(speechTexts);
        String title=AppText.tr("Android-Standardalarm");
        String bundled=ToneCatalog.title(toneUri);
        if (bundled!=null) title=bundled;
        else if (!AlarmLevel.DEFAULT_TONE.equals(toneUri)) {
            Ringtone ringtone=null;
            try { ringtone=RingtoneManager.getRingtone(this,Uri.parse(toneUri)); title=ringtone==null?AppText.tr("Ausgewählter Ton"):ringtone.getTitle(this); }
            catch (RuntimeException e) { title=AppText.tr("Ausgewählter Ton (Verfügbarkeit bitte anhören)"); }
            finally { if (ringtone!=null) try { ringtone.stop(); } catch (RuntimeException e) { /* Release the title lookup player. */ } }
        }
        toneName.setText(title);
    }
    private void chooseTone() {
        stopTone();
        String[] choices={AppText.tr("Samt · ")+LevelForm.kindLabel(kind),AppText.tr("Klar · ")+LevelForm.kindLabel(kind),AppText.tr("Rund · ")+LevelForm.kindLabel(kind),AppText.tr("Eigener Android-Ton …")};
        new AlertDialog.Builder(this).setTitle(AppText.tr("Ton für diese Alarmart")).setItems(choices,(dialog,index) -> {
            if (index==3) { chooseAndroidTone(); return; }
            toneUri=ToneCatalog.uri(ToneCatalog.SETS.get(index),kind); updateControls(); playTone();
        }).setNegativeButton(AppText.tr("Abbrechen"),null).show();
    }
    private void chooseAndroidTone() {
        stopTone();
        Intent intent=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,AppText.tr("Ton für diese Alarmstufe"))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALL)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,Uri.parse(AlarmLevel.DEFAULT_TONE))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(ToneCatalog.setOf(toneUri)==null?toneUri:AlarmLevel.DEFAULT_TONE));
        try { startActivityForResult(intent,PICK_TONE); }
        catch (android.content.ActivityNotFoundException e) { error.setText(AppText.tr("Androids Tonauswahl ist hier nicht verfügbar.")); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==PICK_TONE && result==RESULT_OK && data!=null) {
            Uri uri=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri!=null) {
                if ((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0) {
                    try { getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                    catch (SecurityException e) { /* System ringtone URIs need no persisted document grant. */ }
                }
                toneUri=uri.toString(); updateControls();
            }
        }
    }
    private String selectedVibration() { return VibrationCatalog.values()[vibrationPattern.getSelectedItemPosition()].name(); }
    private void playTone() {
        if (audio!=null && tone.isChecked()) audio.tone(toneUri);
    }
    private void stopTone() {
        if (audio!=null) audio.stop();
    }
    private void save() {
        if (!ready) return;
        try {
            AlarmLevel level=LevelForm.parseWithUnit(id,kind,name.getText().toString(),threshold.getText().toString(),
                    duration==null?"":duration.getText().toString(),repeat==null?"":repeat.getText().toString(),snooze==null?"":snooze.getText().toString(),
                    enabled.isChecked(),tone.isChecked(),speech.isChecked(),toneUri,allDay.isChecked(),
                    allDay.isChecked() && startMinute<0?0:startMinute,allDay.isChecked() && endMinute<0?1440:endMinute,editingUnit,threshold.getText().toString().equals(initialThreshold)?existingThreshold:null);
            level=new AlarmLevel(level.rule,level.name,level.toneUri,SpeechTemplate.variants(speechTexts.getText().toString(),kind),vibration.isChecked()?selectedVibration():null,recovery!=null && recovery.isChecked());
            List<AlarmLevel> desired=new ArrayList<>(); boolean replaced=false;
            for (AlarmLevel current:snapshot.levels) {
                if (current.rule.id.equals(id)) { desired.add(level); replaced=true; } else desired.add(current);
            }
            if (!replaced) desired.add(level);
            OutputRequirements.require(Collections.singletonList(level),new OutputPreferenceStore(getFilesDir()).load().preferences);
            store.save(expectedRevision,desired); RuntimeStateBridge.refresh(getFilesDir(),RuntimeClock.read(this)); LiveAlarmService.settingsChanged(); stopTone();
            Toast.makeText(this,AppText.tr("Stufe gespeichert"),Toast.LENGTH_LONG).show(); finish();
        } catch (IllegalArgumentException e) { error.setText(e.getMessage()); }
        catch (LevelStore.Conflict e) { error.setText(e.getMessage()); save.setEnabled(false); }
        catch (IOException e) { error.setText(AppText.tr("Speichern fehlgeschlagen. Bisherige Einstellungen bleiben unverändert.")); }
    }
    private ArrayList<String> values() {
        return new ArrayList<>(Arrays.asList(name.getText().toString(),threshold.getText().toString(),duration==null?"":duration.getText().toString(),
                repeat==null?"":repeat.getText().toString(),snooze==null?"":snooze.getText().toString(),Boolean.toString(enabled.isChecked()),Boolean.toString(tone.isChecked()),
                Boolean.toString(speech.isChecked()),Boolean.toString(allDay.isChecked()),toneUri,Integer.toString(startMinute),Integer.toString(endMinute),speechTexts.getText().toString(),Boolean.toString(vibration.isChecked()),selectedVibration(),Boolean.toString(recovery!=null && recovery.isChecked())));
    }
    private void restore(Bundle state) {
        ArrayList<String> fields=state.getStringArrayList("fields");
        if (fields==null || (fields.size()!=12 && fields.size()!=13 && fields.size()!=15 && fields.size()!=16)) return;
        name.setText(fields.get(0)); threshold.setText(fields.get(1)); if (duration!=null) duration.setText(fields.get(2));
        if (repeat!=null) repeat.setText(fields.get(3)); if (snooze!=null) snooze.setText(fields.get(4)); enabled.setChecked(Boolean.parseBoolean(fields.get(5)));
        tone.setChecked(Boolean.parseBoolean(fields.get(6))); speech.setChecked(Boolean.parseBoolean(fields.get(7)));
        allDay.setChecked(Boolean.parseBoolean(fields.get(8))); toneUri=fields.get(9);
        startMinute=Integer.parseInt(fields.get(10)); endMinute=Integer.parseInt(fields.get(11));
        if (fields.size()>=13) speechTexts.setText(fields.get(12));
        if(fields.size()>=15) { vibration.setChecked(Boolean.parseBoolean(fields.get(13))); vibrationPattern.setSelection(VibrationCatalog.valueOf(fields.get(14)).ordinal()); }
        if(fields.size()>=16 && recovery!=null) recovery.setChecked(Boolean.parseBoolean(fields.get(15)));
        ArrayList<String> original=state.getStringArrayList("initial"); if (original!=null) initial=original;
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (!ready) return;
        state.putString("editing_unit",editingUnit.name()); state.putString("draftId",id); state.putLong("revision",expectedRevision); state.putStringArrayList("fields",values()); state.putStringArrayList("initial",initial);
    }
    @Override public void onBackPressed() {
        stopTone();
        if (ready && initial!=null && !initial.equals(values())) {
            new AlertDialog.Builder(this).setTitle(AppText.tr("Änderungen verwerfen?")).setMessage(AppText.tr("Die Änderungen dieser Stufe sind noch nicht gespeichert."))
                    .setNegativeButton(AppText.tr("Weiter bearbeiten"),null).setPositiveButton(AppText.tr("Verwerfen"),(d,w) -> finish()).show();
        } else super.onBackPressed();
    }
    @Override public void onPause() { if (audio!=null) audio.pause(); super.onPause(); }
    @Override public void onDestroy() { if (audio!=null) audio.close(); super.onDestroy(); }
}
