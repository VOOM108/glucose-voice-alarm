// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import de.giesen.camaps.companion.prototype.alarm.AlarmOutputFlow;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Last live alarm only. Fixed codes, no values, text, identifiers or timestamps. */
final class AlarmOutputDiagnostics {
    static final String FILE="alarm-output-status.txt";
    enum Speech { NOT_SELECTED, REQUESTED, WAITING, SUBMITTED, STARTED, COMPLETED, FAILED, STOPPED, EXPIRED }
    enum Signal { NOT_SELECTED, REQUESTED, ACCEPTED, FAILED, STOPPED }
    private final Context context;
    private String kind;
    private Speech speech=Speech.NOT_SELECTED;
    private Signal tone=Signal.NOT_SELECTED,vibration=Signal.NOT_SELECTED;
    private boolean deferred;
    AlarmOutputDiagnostics(Context context){this.context=context.getApplicationContext();}
    void begin(AlarmOutputFlow.Plan plan) {
        kind=plan.kind==null?"GAP":plan.kind.name();deferred=false;
        speech=plan.speech==null?Speech.NOT_SELECTED:Speech.REQUESTED;
        tone=plan.toneUris.isEmpty()?Signal.NOT_SELECTED:Signal.REQUESTED;
        vibration=plan.vibrationPatterns.isEmpty()?Signal.NOT_SELECTED:Signal.REQUESTED;write();
    }
    void speech(Speech value){if(kind!=null && speech!=Speech.NOT_SELECTED){speech=value;write();}}
    void tone(boolean accepted){if(kind!=null && tone!=Signal.NOT_SELECTED){tone=accepted?Signal.ACCEPTED:Signal.FAILED;write();}}
    void vibration(boolean accepted){if(kind!=null && vibration!=Signal.NOT_SELECTED){vibration=accepted?Signal.ACCEPTED:Signal.FAILED;write();}}
    void deferred(){deferred=true;write();}
    void stopped(boolean toneRunning,boolean vibrationRunning){
        if(kind==null)return;
        if(speech==Speech.REQUESTED || speech==Speech.WAITING || speech==Speech.SUBMITTED || speech==Speech.STARTED)speech=Speech.STOPPED;
        if(toneRunning && tone==Signal.ACCEPTED)tone=Signal.STOPPED;
        if(vibrationRunning && vibration==Signal.ACCEPTED)vibration=Signal.STOPPED;write();
    }
    private void write(){
        if(kind==null)return;
        String data="GVA_ALARM_OUTPUT_V1\nversion="+BuildConfig.VERSION_CODE+"\nkind="+kind+"\nspeech="+speech.name()+"\ntone="+tone.name()+"\nvibration="+vibration.name()+"\ndeferred="+deferred+"\ndata_basis=fixed_codes_only\n";
        Path target=new File(context.getCacheDir(),FILE).toPath(),temp=new File(context.getCacheDir(),FILE+".new").toPath();
        try{Files.write(temp,data.getBytes(StandardCharsets.UTF_8));Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException | RuntimeException ignored){}finally{try{Files.deleteIfExists(temp);}catch(IOException | RuntimeException ignored){}}
    }
    static String summary(Context context){
        try{
            File file=new File(context.getCacheDir(),FILE);if(!file.isFile() || file.length()>1024)return AppText.tr("Letzter Alarm: noch kein Ausgabestatus");
            List<String> lines=Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
            if(lines.size()!=8 || !lines.get(0).equals("GVA_ALARM_OUTPUT_V1") || !lines.get(1).equals("version="+BuildConfig.VERSION_CODE)
                    || !lines.get(2).matches("kind=(HIGH|LOW|FALLING|RISING|PERSISTENT_HIGH|GAP)") || !lines.get(6).matches("deferred=(true|false)") || !lines.get(7).equals("data_basis=fixed_codes_only"))throw new IOException();
            Speech speech=Speech.valueOf(value(lines.get(3),"speech="));Signal tone=Signal.valueOf(value(lines.get(4),"tone=")),vibration=Signal.valueOf(value(lines.get(5),"vibration="));
            return AppText.tr("Letzter Alarm")+"\n"+AppText.tr("Sprache:")+" "+speechText(speech)+"\n"+AppText.tr("Ton:")+" "+signalText(tone)+"\n"+AppText.tr("Vibration:")+" "+signalText(vibration)
                    +(lines.get(6).equals("deferred=true")?"\n"+AppText.tr("Ansage nach Stimmenstart ergänzt"):"")+"\n"+AppText.tr("Technischer Status; Hör- und Spürbarkeit bleiben am Gerät zu prüfen.");
        }catch(IOException | IllegalArgumentException e){return AppText.tr("Letzter Alarm: Ausgabestatus nicht verfügbar");}
    }
    private static String value(String text,String prefix)throws IOException{if(!text.startsWith(prefix))throw new IOException();return text.substring(prefix.length());}
    private static String speechText(Speech value){switch(value){
        case NOT_SELECTED:return AppText.tr("nicht ausgewählt");case REQUESTED:return AppText.tr("angefordert");case WAITING:return AppText.tr("wartet auf Stimme, höchstens 10 Sekunden");
        case SUBMITTED:return AppText.tr("an Stimme übergeben");case STARTED:return AppText.tr("gestartet");case COMPLETED:return AppText.tr("von Stimme abgeschlossen");
        case FAILED:return AppText.tr("fehlgeschlagen");case STOPPED:return AppText.tr("abgebrochen");default:return AppText.tr("Stimme nicht rechtzeitig bereit");}}
    private static String signalText(Signal value){switch(value){case NOT_SELECTED:return AppText.tr("nicht ausgewählt");case REQUESTED:return AppText.tr("angefordert");case ACCEPTED:return AppText.tr("an Android übergeben");case FAILED:return AppText.tr("fehlgeschlagen");default:return AppText.tr("abgebrochen");}}
}
