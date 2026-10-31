// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import de.giesen.camaps.companion.prototype.TrendDisplayWords;
import java.io.*;
import java.util.*;

/** Settings only: never receipts, references, consent, snooze deadlines or runtime state. */
public final class ConfigurationSnapshot {
    public static final int MAX_BYTES=LevelStore.MAX_ARCHIVE_BYTES+16384;
    public final LevelStore.Snapshot levels;
    public final OutputPreferenceStore.Snapshot output;
    public final String appearance,soundSet;
    public final boolean nativeDisplay,publicValue;
    public final TrendDisplayWords displayWords;
    public ConfigurationSnapshot(LevelStore.Snapshot levels,OutputPreferenceStore.Snapshot output,String appearance,String soundSet,boolean nativeDisplay,boolean publicValue) {
        this(levels,output,appearance,soundSet,nativeDisplay,publicValue,TrendDisplayWords.defaults());
    }
    public ConfigurationSnapshot(LevelStore.Snapshot levels,OutputPreferenceStore.Snapshot output,String appearance,String soundSet,boolean nativeDisplay,boolean publicValue,TrendDisplayWords displayWords) {
        if(!Arrays.asList("SYSTEM","LIGHT","DARK").contains(appearance) || !ToneCatalog.SETS.contains(soundSet)) throw new IllegalArgumentException(AppText.tr("Ungültige Profileinstellungen"));
        this.levels=Objects.requireNonNull(levels); this.output=Objects.requireNonNull(output);
        OutputRequirements.require(levels.levels,output.preferences);
        this.appearance=appearance; this.soundSet=soundSet; this.nativeDisplay=nativeDisplay; this.publicValue=publicValue;
        this.displayWords=Objects.requireNonNull(displayWords);
    }
    public byte[] encode() throws IOException {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(buffer)) {
            out.writeUTF("XCAM_CONFIGURATION"); out.writeInt(3); out.writeUTF(appearance); out.writeUTF(soundSet); out.writeBoolean(nativeDisplay); out.writeBoolean(publicValue);
            // Revision zero denotes defaults in memory; archives always have a decodable positive revision.
            bytes(out,LevelStore.encode(new LevelStore.Snapshot(Math.max(1,levels.revision),levels.levels)));
            bytes(out,OutputPreferenceStore.encode(new OutputPreferenceStore.Snapshot(Math.max(1,output.revision),output.preferences)));
            for(String word:displayWords.overrides)out.writeUTF(word);
            displayWords.colors.write(out);
        }
        byte[] sealed=ConfigurationFiles.seal(buffer.toByteArray()); if(sealed.length>MAX_BYTES) throw new IOException(AppText.tr("Profil zu groß")); return sealed;
    }
    public static ConfigurationSnapshot decode(byte[] encoded) throws IOException {
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(ConfigurationFiles.open(encoded,MAX_BYTES)))) {
            if(!"XCAM_CONFIGURATION".equals(in.readUTF())) throw new IOException(AppText.tr("Unbekanntes Profilformat"));
            int schema=in.readInt();if(schema<1 || schema>3)throw new IOException(AppText.tr("Unbekanntes Profilformat"));
            String appearance=in.readUTF(),set=in.readUTF(); boolean display=in.readBoolean(),value=in.readBoolean();
            LevelStore.Snapshot levels=LevelStore.decode(bytes(in,LevelStore.MAX_ARCHIVE_BYTES));
            OutputPreferenceStore.Snapshot output=OutputPreferenceStore.decode(bytes(in,8192));
            TrendDisplayWords words=TrendDisplayWords.defaults();
            if(schema>=2) {List<String> entries=new ArrayList<>();for(int i=0;i<TrendDisplayWords.COUNT;i++)entries.add(in.readUTF());words=new TrendDisplayWords(entries);}
            if(schema>=3)words=new TrendDisplayWords(words.overrides,de.giesen.camaps.companion.prototype.TrendColorOverrides.read(in,40));
            ConfigurationSnapshot result=new ConfigurationSnapshot(levels,output,appearance,set,display,value,words);
            if(in.read()!=-1) throw new IOException(AppText.tr("Zusätzliche Profileinstellungen")); return result;
        } catch(IllegalArgumentException e) { throw new IOException(AppText.tr("Ungültiges Profil; unverändert erhalten"),e); }
    }
    static void bytes(DataOutputStream out,byte[] data) throws IOException { out.writeInt(data.length); out.write(data); }
    static byte[] bytes(DataInputStream in,int limit) throws IOException { int count=in.readInt(); if(count<1 || count>limit) throw new IOException(AppText.tr("Ungültige Profillänge")); byte[] data=new byte[count]; in.readFully(data); return data; }
}
