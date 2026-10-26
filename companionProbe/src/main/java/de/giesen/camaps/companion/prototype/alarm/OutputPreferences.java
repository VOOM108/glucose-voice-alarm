// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

/** Global presentation controls; no setting enables live delivery or a disabled rule. */
public final class OutputPreferences {
    public final AlarmEngine.OutputMode mode;
    public final int valueIntervalMinutes;
    public final String valueTemplate;
    public final int toneVolumePercent,speechVolumePercent;
    public final int valueQuietMinutes;
    public final int bellBoostDb;
    public final TrendSpeech trendSpeech;
    public final GapSettings gap;
    public final String speechLanguage;
    public final de.giesen.camaps.companion.prototype.GlucoseUnit.Mode unitMode;
    public final boolean tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled;
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate) {
        this(mode,valueIntervalMinutes,valueTemplate,100,100);
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,5);
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,0);
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,TrendSpeech.defaults());
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb,TrendSpeech trendSpeech) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,true,mode!=AlarmEngine.OutputMode.TONES_ONLY,mode==AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES);
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb,TrendSpeech trendSpeech,boolean tonesEnabled,boolean alarmSpeechEnabled,boolean valueSpeechEnabled) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,false);
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb,TrendSpeech trendSpeech,boolean tonesEnabled,boolean alarmSpeechEnabled,boolean valueSpeechEnabled,boolean vibrationEnabled) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,GapSettings.defaults());
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb,TrendSpeech trendSpeech,boolean tonesEnabled,boolean alarmSpeechEnabled,boolean valueSpeechEnabled,boolean vibrationEnabled,GapSettings gap) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,gap,"de");
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb,TrendSpeech trendSpeech,boolean tonesEnabled,boolean alarmSpeechEnabled,boolean valueSpeechEnabled,boolean vibrationEnabled,GapSettings gap,String speechLanguage) {
        this(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,gap,speechLanguage,de.giesen.camaps.companion.prototype.GlucoseUnit.Mode.AUTO);
    }
    public OutputPreferences(AlarmEngine.OutputMode mode,int valueIntervalMinutes,String valueTemplate,int toneVolumePercent,int speechVolumePercent,int valueQuietMinutes,int bellBoostDb,TrendSpeech trendSpeech,boolean tonesEnabled,boolean alarmSpeechEnabled,boolean valueSpeechEnabled,boolean vibrationEnabled,GapSettings gap,String speechLanguage,de.giesen.camaps.companion.prototype.GlucoseUnit.Mode unitMode) {
        this.unitMode=java.util.Objects.requireNonNull(unitMode);
        if(!"de".equals(speechLanguage) && !"en".equals(speechLanguage)) throw new IllegalArgumentException("Unsupported speech language");
        this.speechLanguage=speechLanguage;
        this.gap=java.util.Objects.requireNonNull(gap);
        if (mode==null || valueIntervalMinutes<0 || valueIntervalMinutes>Integer.MAX_VALUE/60000
                || (valueSpeechEnabled && valueIntervalMinutes<1))
            throw new IllegalArgumentException(AppText.tr("Für Wertansagen bitte ein positives Intervall in ganzen Minuten eingeben"));
        if (toneVolumePercent<0 || toneVolumePercent>100 || speechVolumePercent<0 || speechVolumePercent>100)
            throw new IllegalArgumentException(AppText.tr("Lautstärke muss zwischen 0 und 100 Prozent liegen"));
        if (valueQuietMinutes<0 || valueQuietMinutes>Integer.MAX_VALUE/60000) throw new IllegalArgumentException(AppText.tr("Ungültige Pause für Wertansagen"));
        if (bellBoostDb<0 || bellBoostDb>ToneCatalog.MAX_BOOST_DB) throw new IllegalArgumentException("Glockenton-Boost muss zwischen 0 und 6 dB liegen");
        SpeechTemplate.validate(valueTemplate,null,true);
        this.mode=mode; this.valueIntervalMinutes=valueIntervalMinutes; this.valueTemplate=valueTemplate;
        this.toneVolumePercent=toneVolumePercent; this.speechVolumePercent=speechVolumePercent;
        this.valueQuietMinutes=valueQuietMinutes;
        this.bellBoostDb=bellBoostDb;
        this.trendSpeech=java.util.Objects.requireNonNull(trendSpeech);
        if(!tonesEnabled && !alarmSpeechEnabled && !vibrationEnabled) throw new IllegalArgumentException(AppText.tr("Mindestens Ton, Alarm-Sprache oder Vibration muss eingeschaltet bleiben"));
        this.vibrationEnabled=vibrationEnabled;
        this.tonesEnabled=tonesEnabled; this.alarmSpeechEnabled=alarmSpeechEnabled; this.valueSpeechEnabled=valueSpeechEnabled;
    }
    /** Keeps existing manually requested speech possible, but enables no periodic/live output. */
    public static OutputPreferences defaults() { String language=de.giesen.camaps.companion.prototype.AppText.language(); return new OutputPreferences(AlarmEngine.OutputMode.SPOKEN_ALARMS,0,SpeechTemplate.valueDefault(language),100,100,5,0,TrendSpeech.defaults(language)).withSpeechLanguage(language); }
    public boolean speechAllowed() { return alarmSpeechEnabled || valueSpeechEnabled; }
    public boolean periodicAllowed() { return valueSpeechEnabled && valueIntervalMinutes>0 && speechVolumePercent>0; }
    public boolean alarmSpeechAllowed(AlarmLevel level) { return alarmSpeechEnabled && speechVolumePercent>0 && level.rule.enabled && level.rule.outputs.speech; }
    public boolean alarmToneAllowed(AlarmLevel level) { return tonesEnabled && toneVolumePercent>0 && level.rule.enabled && level.rule.outputs.tone; }
    public boolean alarmVibrationAllowed(AlarmLevel level) { return vibrationEnabled && level.rule.enabled && level.vibrationPattern!=null; }
    public boolean audibleAlarmRoute() { return tonesEnabled && toneVolumePercent>0 || alarmSpeechEnabled && speechVolumePercent>0 || vibrationEnabled; }
    public AlarmEngine.OutputMode engineMode() { return alarmSpeechEnabled?AlarmEngine.OutputMode.SPOKEN_ALARMS:AlarmEngine.OutputMode.TONES_ONLY; }
    public OutputPreferences withChannels(boolean tones,boolean alarms,boolean values) {
        return withChannels(tones,alarms,values,vibrationEnabled);
    }
    public OutputPreferences withChannels(boolean tones,boolean alarms,boolean values,boolean vibration) {
        AlarmEngine.OutputMode mode=values?AlarmEngine.OutputMode.SPOKEN_ALARMS_AND_VALUES:alarms?AlarmEngine.OutputMode.SPOKEN_ALARMS:AlarmEngine.OutputMode.TONES_ONLY;
        return new OutputPreferences(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tones,alarms,values,vibration,gap,speechLanguage,unitMode);
    }
    public OutputPreferences withGap(GapSettings gap) { return new OutputPreferences(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,gap,speechLanguage,unitMode); }
    public OutputPreferences withTrendSpeech(TrendSpeech words) { return new OutputPreferences(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,words,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,gap,speechLanguage,unitMode); }
    public OutputPreferences withSpeechLanguage(String language) { return new OutputPreferences(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,gap,language,unitMode); }
    public OutputPreferences withUnitMode(de.giesen.camaps.companion.prototype.GlucoseUnit.Mode unit) { return new OutputPreferences(mode,valueIntervalMinutes,valueTemplate,toneVolumePercent,speechVolumePercent,valueQuietMinutes,bellBoostDb,trendSpeech,tonesEnabled,alarmSpeechEnabled,valueSpeechEnabled,vibrationEnabled,gap,speechLanguage,unit); }
    public static String modeLabel(AlarmEngine.OutputMode mode) {
        switch(mode) {
            case TONES_ONLY: return AppText.tr("Nur Töne");
            case SPOKEN_ALARMS: return AppText.tr("Gesprochene Alarme");
            case SPOKEN_ALARMS_AND_VALUES: return AppText.tr("Gesprochene Alarme plus Werte");
            default: throw new IllegalArgumentException();
        }
    }
}
