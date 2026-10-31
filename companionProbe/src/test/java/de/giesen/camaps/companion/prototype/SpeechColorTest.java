// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import de.giesen.gva.watch.ArrowColor;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeechColorTest {
    private SpeechTemplate.Values values(ArrowColor color,NotificationParser.Trend trend,String language) {
        return new SpeechTemplate.Values(123,trend,0,0,TrendSpeech.defaults(),language,GlucoseUnit.MGDL,color);
    }
    @Test public void explicitColorIsLiteralAndNormalIsEmptyWithoutExtraWhitespace() {
        for(ArrowColor color:ArrowColor.values()) {
            String suffix=color==ArrowColor.RED?" rot":color==ArrowColor.YELLOW?" gelb":"";
            assertEquals("123 stabil"+suffix,SpeechTemplate.render("{wert} {trend} {farbe}",null,values(color,NotificationParser.Trend.FLAT,"de"),true,false));
        }
    }
    @Test public void englishAliasUsesSpeechLanguageAndUnknownHasNoColor() {
        assertEquals("123 red",SpeechTemplate.render("{value} {color}",null,values(ArrowColor.RED,NotificationParser.Trend.FLAT,"en"),true,false));
        assertEquals("123 yellow",SpeechTemplate.render("{value} {color}",null,values(ArrowColor.YELLOW,NotificationParser.Trend.FLAT,"en"),true,false));
        assertEquals("123",SpeechTemplate.render("{wert} {farbe}",null,values(ArrowColor.RED,NotificationParser.Trend.UNKNOWN,"de"),true,false));
    }
    @Test public void existingTextDoesNotAcquireUnrequestedColorAndAllAlarmKindsAllowToken() {
        assertEquals("123 stabil",SpeechTemplate.render("{wert} {trend}",null,values(ArrowColor.RED,NotificationParser.Trend.FLAT,"de"),true,false));
        for(AlarmEngine.Kind kind:AlarmEngine.Kind.values()) SpeechTemplate.validate("{wert} {trend} {farbe}",kind,false);
        assertTrue(SyntheticSpeech.value("{wert} {trend} {farbe}").endsWith("123 stabil rot"));
    }
}
