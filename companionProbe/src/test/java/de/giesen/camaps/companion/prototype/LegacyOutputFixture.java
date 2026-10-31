// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.*;
/** Historical output layouts written independently of the current production encoder. */
final class LegacyOutputFixture {
    static byte[] encode(int schema,long revision,OutputPreferences p,String first,String second)throws IOException{
        if(schema<1 || schema>11)throw new IllegalArgumentException();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream o=new DataOutputStream(bytes)){
            o.writeUTF("XCAM_OUTPUT");o.writeInt(schema);o.writeLong(revision);o.writeUTF(p.mode.name());o.writeInt(p.valueIntervalMinutes);o.writeUTF(p.valueTemplate);
            if(schema>=2){o.writeInt(p.toneVolumePercent);o.writeInt(p.speechVolumePercent);o.writeInt(p.valueQuietMinutes);}if(schema>=3)o.writeInt(p.bellBoostDb);
            if(schema>=4)for(String word:p.trendSpeech.words.subList(0,5))o.writeUTF(word);
            if(schema>=5){o.writeBoolean(p.tonesEnabled);o.writeBoolean(p.alarmSpeechEnabled);o.writeBoolean(p.valueSpeechEnabled);}if(schema>=6)o.writeBoolean(p.vibrationEnabled);
            if(schema>=7)p.gap.write(o);
            if(schema>=8){o.writeUTF(p.speechLanguage);o.writeUTF(p.trendSpeech.words.get(5));o.writeUTF(p.trendSpeech.words.get(6));o.writeUTF(p.gap.speechTemplate);}
            if(schema>=9){o.writeUTF(first);o.writeUTF(second);}if(schema>=10)for(boolean flag:p.trendSpeech.enabled)o.writeBoolean(flag);if(schema>=11)o.writeUTF(p.unitMode.name());
        }
        return ConfigurationFiles.seal(bytes.toByteArray());
    }
}
