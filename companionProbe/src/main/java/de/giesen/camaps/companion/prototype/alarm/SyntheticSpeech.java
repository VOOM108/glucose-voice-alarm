// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;

import de.giesen.camaps.companion.prototype.NotificationParser.Trend;
import java.util.*;

/** Fixed speech fixtures only: never copied into rule forms or used for live decisions. */
public final class SyntheticSpeech {
    public static String alarm(AlarmEngine.Kind kind,String name,String lines,long variant) {
        return alarm(kind,name,lines,variant,TrendSpeech.defaults());
    }
    public static String alarm(AlarmEngine.Kind kind,String name,String lines,long variant,TrendSpeech words) {
        return alarm(kind,name,lines,variant,words,"de");
    }
    public static String alarm(AlarmEngine.Kind kind,String name,String lines,long variant,TrendSpeech words,String language) {
        return alarm(kind,name,lines,variant,words,language,de.giesen.camaps.companion.prototype.GlucoseUnit.MGDL);
    }
    public static String alarm(AlarmEngine.Kind kind,String name,String lines,long variant,TrendSpeech words,String language,de.giesen.camaps.companion.prototype.GlucoseUnit unit) {
        List<String> variants=SpeechTemplate.variants(lines,kind);
        AlarmEngine.Rule rule=new AlarmEngine.Rule("speech_fixture",kind,false,
                LevelForm.isRate(kind)?2:kind==AlarmEngine.Kind.LOW?80:180,kind==AlarmEngine.Kind.PERSISTENT_HIGH?10:0,true,0,1440,
                new AlarmEngine.Outputs(false,false,LevelForm.hasRepeat(kind)?3:0,LevelForm.hasSnooze(kind)?5:0));
        AlarmLevel level=new AlarmLevel(rule,name==null || name.trim().isEmpty()?"Teststufe":name,AlarmLevel.DEFAULT_TONE,variants);
        boolean low=kind==AlarmEngine.Kind.LOW || kind==AlarmEngine.Kind.FALLING;
        return SpeechTemplate.render(level.speechText((int)Math.floorMod(variant,Math.max(1,variants.size())),language),level,
                new SpeechTemplate.Values(low?65:230,low?Trend.DOWN:Trend.UP,0,11,words,language,unit),false,true);
    }
    public static String value(String template) {
        return value(template,TrendSpeech.defaults());
    }
    public static String value(String template,TrendSpeech words) {
        return value(template,words,"de");
    }
    public static String value(String template,TrendSpeech words,String language) {
        return value(template,words,language,de.giesen.camaps.companion.prototype.GlucoseUnit.MGDL);
    }
    public static String value(String template,TrendSpeech words,String language,de.giesen.camaps.companion.prototype.GlucoseUnit unit) {
        return SpeechTemplate.render(template,null,new SpeechTemplate.Values(123,Trend.FLAT,10,0,words,language,unit),true,true);
    }
    private SyntheticSpeech() {}
}
