// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded technical snapshots, never a measurement or event history. Main-thread owner. */
final class ProcessingDiagnostics {
    static final String FILE="processing-status.txt";
    enum Card { NONE, STARTED, REFRESHED, CLEARED }
    enum Output { NONE, ALARM, VALUE, GAP }
    private final Context context;
    private long receipt,dispatch;
    private boolean capturing;
    private final LinkedHashMap<String,String> data=new LinkedHashMap<>();
    ProcessingDiagnostics(Context context) {
        this.context=context.getApplicationContext();
        AlarmOutputDiagnostics.reset(this.context);
        data.put("receipt","0"); resetReceipt();
        for(String prefix:new String[]{"last_output","last_alarm"}) {
            data.put(prefix+"_number","0"); data.put(prefix+"_receipt","0"); data.put(prefix+"_type","NONE");
            data.put(prefix+"_kind","NONE"); data.put(prefix+"_accepted","false");
            data.put(prefix+"_speech","false");data.put(prefix+"_tone","false");data.put(prefix+"_vibration","false");
        }
        write();
    }
    private void resetReceipt() {
        data.put("evaluation","NONE");data.put("threshold","NONE");data.put("threshold_outcome","NONE");
        data.put("threshold_wait","NONE");data.put("threshold_guard","NONE");data.put("value_outcome","NONE");
        data.put("card","NONE");data.put("receipt_output","NONE");
    }
    void beginReceipt(){receipt=receipt==Long.MAX_VALUE?1:receipt+1;capturing=true;data.put("receipt",Long.toString(receipt));resetReceipt();}
    void evaluated(AlarmOutputFlow.Outcome outcome,AlarmEngine.Result result){
        if(!capturing)return;data.put("evaluation",outcome.name());
        if(result!=null)for(AlarmEngine.Decision d:result.decisions)if(d.rule.id.equals(result.selectedThresholdRule))data.put("threshold",d.rule.kind.name());
    }
    boolean capturing(){return capturing;}
    long receiptNumber(){return receipt;}
    long nextDispatchNumber(){return dispatch==Long.MAX_VALUE?1:dispatch+1;}
    void threshold(AlarmOutputFlow.Outcome outcome,AlarmOutputFlow.WaitReason wait,OutputGuard.Reason guard){
        if(!capturing)return;data.put("threshold_outcome",outcome.name());data.put("threshold_wait",wait.name());
        data.put("threshold_guard",outcome==AlarmOutputFlow.Outcome.SUPPRESSED?guard.name():"NONE");
    }
    void value(AlarmOutputFlow.Outcome outcome){if(capturing)data.put("value_outcome",outcome.name());}
    void card(Card card){if(capturing && !data.get("card").equals("STARTED"))data.put("card",card.name());}
    void dispatched(AlarmOutputFlow.Plan plan,boolean accepted){
        dispatch=nextDispatchNumber();
        Output type=plan.ruleId==null?Output.VALUE:plan.kind==null?Output.GAP:Output.ALARM;
        if(capturing)data.put("receipt_output",type.name());
        record("last_output",plan,type,accepted);
        if(type!=Output.VALUE)record("last_alarm",plan,type,accepted);
        if(!capturing)write();
    }
    private void record(String prefix,AlarmOutputFlow.Plan plan,Output type,boolean accepted){
        data.put(prefix+"_number",Long.toString(dispatch));data.put(prefix+"_receipt",Long.toString(receipt));
        data.put(prefix+"_type",type.name());data.put(prefix+"_kind",plan.kind==null?"NONE":plan.kind.name());
        data.put(prefix+"_accepted",Boolean.toString(accepted));data.put(prefix+"_speech",Boolean.toString(plan.speech!=null));
        data.put(prefix+"_tone",Boolean.toString(!plan.toneUris.isEmpty()));data.put(prefix+"_vibration",Boolean.toString(!plan.vibrationPatterns.isEmpty()));
    }
    void finishReceipt(){capturing=false;write();}
    private void write(){
        StringBuilder text=new StringBuilder("GVA_PROCESSING_V1\nversion="+BuildConfig.VERSION_CODE+"\n");
        for(Map.Entry<String,String> row:data.entrySet())text.append(row.getKey()).append('=').append(row.getValue()).append('\n');
        Path target=new File(context.getCacheDir(),FILE).toPath(),temp=new File(context.getCacheDir(),FILE+".new").toPath();
        try{Files.write(temp,text.toString().getBytes(StandardCharsets.UTF_8));Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException | RuntimeException ignored){}finally{try{Files.deleteIfExists(temp);}catch(IOException | RuntimeException ignored){}}
    }
    static Map<String,String> read(Context context)throws IOException {
        File file=new File(context.getCacheDir(),FILE);if(file.length()>4096)throw new IOException();
        List<String> lines=Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
        if(lines.size()!=27 || !lines.get(0).equals("GVA_PROCESSING_V1") || !lines.get(1).equals("version="+BuildConfig.VERSION_CODE))throw new IOException();
        Map<String,String> result=new LinkedHashMap<>();
        for(int i=2;i<lines.size();i++){
            String[] pair=lines.get(i).split("=",-1);if(pair.length!=2 || result.put(pair[0],pair[1])!=null || !valid(pair[0],pair[1]))throw new IOException();
        }
        // Exact keys, not merely a count: no arbitrary strings may enter the UI/export.
        Set<String> keys=new HashSet<>(Arrays.asList("receipt","evaluation","threshold","threshold_outcome","threshold_wait","threshold_guard","value_outcome","card","receipt_output"));
        for(String prefix:new String[]{"last_output","last_alarm"})for(String suffix:new String[]{"number","receipt","type","kind","accepted","speech","tone","vibration"})keys.add(prefix+"_"+suffix);
        if(!result.keySet().equals(keys))throw new IOException();return Collections.unmodifiableMap(result);
    }
    private static boolean valid(String key,String value){
        try{
            if(key.equals("receipt") || key.endsWith("_number") || key.endsWith("_receipt"))return value.matches("0|[1-9][0-9]{0,18}") && Long.parseLong(value)>=0;
            if(key.equals("threshold") || key.endsWith("_kind"))return value.equals("NONE") || AlarmEngine.Kind.valueOf(value)!=null;
            if(key.equals("evaluation") || key.endsWith("_outcome"))return value.equals("NONE") || AlarmOutputFlow.Outcome.valueOf(value)!=null;
            if(key.equals("threshold_wait"))return AlarmOutputFlow.WaitReason.valueOf(value)!=null;
            if(key.equals("threshold_guard"))return OutputGuard.Reason.valueOf(value)!=null;
            if(key.equals("card"))return Card.valueOf(value)!=null;
            if(key.equals("receipt_output") || key.endsWith("_type"))return Output.valueOf(value)!=null;
            return (key.endsWith("_accepted") || key.endsWith("_speech") || key.endsWith("_tone") || key.endsWith("_vibration")) && (value.equals("true") || value.equals("false"));
        }catch(IllegalArgumentException e){return false;}
    }
    static String summary(Context context){
        try{
            Map<String,String> d=read(context);
            if(d.get("receipt").equals("0"))return AppText.tr("Verarbeitung: wartet auf neuen Eingang");
            String text=AppText.tr("Letzter neuer Eingang")+" #"+d.get("receipt")+"\n"+AppText.tr("Prüfung:")+" "+outcome(d.get("evaluation"));
            text+="\n"+AppText.tr("Grenzalarm:")+" "+kind(d.get("threshold"))+" · "+outcome(d.get("threshold_outcome"));
            String wait=d.get("threshold_wait");if(!wait.equals("NONE"))text+=" · "+waitText(wait);
            if(d.get("threshold_guard").equals("GLOBAL_PAUSE"))text+=" · "+AppText.tr("globale Pause");
            if(d.get("threshold_guard").equals("RECOVERING_TREND"))text+=" · "+AppText.tr("Erholungstrend");
            text+="\n"+AppText.tr("Alarmkarte:")+" "+cardText(d.get("card"));
            text+="\n"+AppText.tr("Wertansage:")+" "+outcome(d.get("value_outcome"));
            text+="\n\n"+dispatchText(d,"last_alarm",AppText.tr("Letzter Alarmversuch"));
            text+="\n\n"+dispatchText(d,"last_output",AppText.tr("Letzter Ausgabeversuch"));
            return text+"\n\n"+AppText.tr("Eingangsnummern verknüpfen die Angaben innerhalb dieses Dienststarts. Keine Messwerte oder Ansagetexte gespeichert. Annahme bestätigt keine Hörbarkeit.");
        }catch(IOException | RuntimeException e){return AppText.tr("Verarbeitung: Diagnose nicht verfügbar");}
    }
    private static String dispatchText(Map<String,String> d,String p,String title){
        if(d.get(p+"_number").equals("0"))return title+": "+AppText.tr("noch keiner");
        String type=d.get(p+"_type");String text=title+" #"+d.get(p+"_number")+" · "+AppText.tr("Eingang")+" #"+d.get(p+"_receipt");
        text+="\n"+(type.equals("VALUE")?AppText.tr("normale Wertansage"):type.equals("GAP")?AppText.tr("Lückenwarnung"):AppText.tr("Alarm")+" · "+kind(d.get(p+"_kind")));
        text+=" · "+(d.get(p+"_accepted").equals("true")?AppText.tr("Ausgabe angenommen"):AppText.tr("Ausgabe abgelehnt"));
        text+="\n"+AppText.tr("Angefordert:")+" "+AppText.tr("Sprache:")+" "+yes(d.get(p+"_speech"))+", "+AppText.tr("Ton:")+" "+yes(d.get(p+"_tone"))+", "+AppText.tr("Vibration:")+" "+yes(d.get(p+"_vibration"));return text;
    }
    private static String yes(String value){return value.equals("true")?AppText.tr("ja"):AppText.tr("nein");}
    private static String kind(String code){return code.equals("NONE")?AppText.tr("keiner"):de.giesen.camaps.companion.prototype.alarm.LevelForm.kindLabel(AlarmEngine.Kind.valueOf(code));}
    private static String cardText(String code){switch(Card.valueOf(code)){case STARTED:return AppText.tr("neuer Alarmstart");case REFRESHED:return AppText.tr("still aktualisiert");case CLEARED:return AppText.tr("entfernt");default:return AppText.tr("unverändert");}}
    private static String waitText(String code){switch(AlarmOutputFlow.WaitReason.valueOf(code)){case REPEAT:return AppText.tr("Wiederholungsfrist");case SNOOZE:return AppText.tr("Snooze");case OTHER_LEVEL:return AppText.tr("Frist einer anderen Stufe");default:return AppText.tr("Fristgrund nicht lesbar");}}
    private static String outcome(String code){
        if(code.equals("NONE"))return AppText.tr("nicht aufgerufen");
        switch(AlarmOutputFlow.Outcome.valueOf(code)){
            case ACCEPTED:return AppText.tr("angenommen");case NOT_DUE:return AppText.tr("noch nicht fällig");case OUTPUT_UNAVAILABLE:return AppText.tr("Ausgabe nicht bereit oder belegt");
            case ATTEMPT_FAILED:return AppText.tr("Ausgabeversuch fehlgeschlagen");case STORAGE_BLOCKED:return AppText.tr("Zustandsspeicher blockiert");
            case DATA_BLOCKED:return AppText.tr("Daten nicht verwendbar");case LIVE_BLOCKED:return AppText.tr("Live-Ausgabe gesperrt");case DISABLED:return AppText.tr("ausgeschaltet");
            case CONDITION_FALSE:return AppText.tr("Bedingung nicht erfüllt");case PAUSED:return AppText.tr("pausiert");default:return AppText.tr("durch Regel ausgesetzt");
        }
    }
}
