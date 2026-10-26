// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.io.*;
import java.util.*;

/** Independent loss-of-observation warning; never predicts or supplies glucose samples. */
public final class GapSettings {
    public final boolean enabled,onlyAfterFast,tone,speech;
    public final int delayMinutes,repeatMinutes;
    public final String toneUri,vibrationPattern;
    public final String speechTemplate;
    public GapSettings(boolean enabled,boolean onlyAfterFast,int delayMinutes,int repeatMinutes,boolean tone,boolean speech,String toneUri,String vibrationPattern) {
        this(enabled,onlyAfterFast,delayMinutes,repeatMinutes,tone,speech,toneUri,vibrationPattern,"");
    }
    public GapSettings(boolean enabled,boolean onlyAfterFast,int delayMinutes,int repeatMinutes,boolean tone,boolean speech,String toneUri,String vibrationPattern,String speechTemplate) {
        GapSpeechTemplate.validate(speechTemplate); this.speechTemplate=speechTemplate;
        if(delayMinutes<1 || delayMinutes>1440 || repeatMinutes<0 || repeatMinutes>1440) throw new IllegalArgumentException(AppText.tr("Wartezeit: 1–1440 Minuten; Wiederholung: 0–1440 Minuten"));
        if(toneUri==null || toneUri.length()>2048 || toneUri.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(AppText.tr("Ungültiger Ton"));
        java.net.URI uri=java.net.URI.create(toneUri); if(!("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme()))) throw new IllegalArgumentException(AppText.tr("Bitte einen lokalen Android-Ton auswählen"));
        this.enabled=enabled; this.onlyAfterFast=onlyAfterFast; this.delayMinutes=delayMinutes; this.repeatMinutes=repeatMinutes;
        this.tone=tone; this.speech=speech; this.toneUri=toneUri; this.vibrationPattern=VibrationCatalog.validate(vibrationPattern);
    }
    public static GapSettings defaults() { return new GapSettings(false,false,4,5,true,true,ToneCatalog.uri(ToneCatalog.DEFAULT_SET,AlarmEngine.Kind.PERSISTENT_HIGH),null); }
    public GapSettings withTemplate(String template) { return new GapSettings(enabled,onlyAfterFast,delayMinutes,repeatMinutes,tone,speech,toneUri,vibrationPattern,template); }
    public boolean toneAllowed(OutputPreferences p) { return enabled && tone && p.tonesEnabled && p.toneVolumePercent>0; }
    public boolean speechAllowed(OutputPreferences p) { return enabled && speech && p.alarmSpeechEnabled && p.speechVolumePercent>0; }
    public boolean vibrationAllowed(OutputPreferences p) { return enabled && vibrationPattern!=null && p.vibrationEnabled; }
    public void require(OutputPreferences p) { if(enabled && !(toneAllowed(p) || speechAllowed(p) || vibrationAllowed(p))) throw new IllegalArgumentException(AppText.tr("Für die Lückenwarnung fehlt ein aktiver Ausgabeweg")); }
    public void write(DataOutputStream out) throws IOException {
        out.writeBoolean(enabled); out.writeBoolean(onlyAfterFast); out.writeInt(delayMinutes); out.writeInt(repeatMinutes);
        out.writeBoolean(tone); out.writeBoolean(speech); out.writeUTF(toneUri); out.writeUTF(vibrationPattern==null?"":vibrationPattern);
    }
    public static GapSettings read(DataInputStream in) throws IOException {
        boolean enabled=in.readBoolean(),only=in.readBoolean(); int delay=in.readInt(),repeat=in.readInt(); boolean tone=in.readBoolean(),speech=in.readBoolean(); String uri=in.readUTF(),pattern=in.readUTF();
        return new GapSettings(enabled,only,delay,repeat,tone,speech,uri,pattern.isEmpty()?null:pattern);
    }
}
