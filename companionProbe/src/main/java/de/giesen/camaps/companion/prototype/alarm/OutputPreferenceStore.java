// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class OutputPreferenceStore {
    public static final String FILE_NAME="alarm-output-v1.bin";
    private static final Object LOCK=ConfigurationFiles.LOCK;
    private final File directory;
    public static final class Snapshot {
        public final long revision;
        public final OutputPreferences preferences;
        public Snapshot(long revision,OutputPreferences preferences) { if(revision<0) throw new IllegalArgumentException(AppText.tr("Ungültige Ausgabe-Revision")); this.revision=revision; this.preferences=Objects.requireNonNull(preferences); }
    }
    public OutputPreferenceStore(File directory) { this.directory=Objects.requireNonNull(directory); }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Snapshot load() throws IOException {
        synchronized(LOCK) {
            SettingsTransaction.recoverCore(directory);
            Path path=new File(directory,FILE_NAME).toPath();
            if (!Files.exists(path)) return new Snapshot(0,OutputPreferences.defaults());
            return decode(ConfigurationFiles.read(path.toFile(),8192));
        }
    }
    public static Snapshot decode(byte[] encoded) throws IOException {
            byte[] body=ConfigurationFiles.open(encoded,8192);
            try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(body))) {
                if (!"XCAM_OUTPUT".equals(in.readUTF())) throw new IOException(AppText.tr("Unbekannte Ausgabe-Einstellungen"));
                int schema=in.readInt(); if (schema<1 || schema>11) throw new IOException(AppText.tr("Unbekannte Ausgabe-Einstellungen"));
                long revision=in.readLong(); if (revision<1) throw new IOException(AppText.tr("Ungültige Ausgabe-Revision"));
                AlarmEngine.OutputMode mode=AlarmEngine.OutputMode.valueOf(in.readUTF()); int minutes=in.readInt(); String template=in.readUTF();
                int tone=schema>=2?in.readInt():100,speech=schema>=2?in.readInt():100,quiet=schema>=2?in.readInt():5,boost=schema>=3?in.readInt():0;
                // Schemas 1–3 never stored trend phrases. Keep their historical fallback;
                // new defaults apply only to new settings or an explicit reset in the editor.
                TrendSpeech words=new TrendSpeech(Arrays.asList("steigend","schnell steigend","stabil","fallend","schnell fallend"));
                if(schema>=4) { List<String> entries=new ArrayList<>(); for(int i=0;i<5;i++) entries.add(in.readUTF()); words=new TrendSpeech(entries); }
                OutputPreferences preferences=schema>=5?new OutputPreferences(mode,minutes,template,tone,speech,quiet,boost,words,in.readBoolean(),in.readBoolean(),in.readBoolean(),schema>=6 && in.readBoolean()):new OutputPreferences(mode,minutes,template,tone,speech,quiet,boost,words);
                if(schema>=7) preferences=preferences.withGap(GapSettings.read(in));
                if(schema>=8) {
                    String language=in.readUTF(); List<String> all=new ArrayList<>(words.words.subList(0,5)); all.add(in.readUTF()); all.add(in.readUTF());
                    preferences=new OutputPreferences(mode,minutes,template,tone,speech,quiet,boost,new TrendSpeech(all),preferences.tonesEnabled,preferences.alarmSpeechEnabled,preferences.valueSpeechEnabled,preferences.vibrationEnabled,preferences.gap.withTemplate(in.readUTF()),language);
                }
                if(schema>=9) { readRetiredSource(in); readRetiredSource(in); }
                if(schema>=10) {
                    List<Boolean> enabled=new ArrayList<>(); for(int i=0;i<TrendSpeech.COUNT;i++) enabled.add(in.readBoolean());
                    preferences=preferences.withTrendSpeech(new TrendSpeech(preferences.trendSpeech.words,enabled));
                }
                if(schema>=11) preferences=preferences.withUnitMode(de.giesen.camaps.companion.prototype.GlucoseUnit.Mode.valueOf(in.readUTF()));
                if (in.read()!=-1) throw new IOException(AppText.tr("Zusätzliche Ausgabe-Einstellungen"));
                return new Snapshot(revision,preferences);
            } catch (IllegalArgumentException e) { throw new IOException(AppText.tr("Ungültige Ausgabe-Einstellungen"),e); }
    }
    private static void readRetiredSource(DataInputStream in) throws IOException {
        String value=in.readUTF();
        if(!"CAMAPS".equals(value) && !"CALCULATED".equals(value)) throw new IOException("Invalid retired trend source");
    }
    public Snapshot save(long revision,OutputPreferences preferences) throws IOException {
        synchronized(LOCK) {
            if (load().revision!=revision) throw new IOException(AppText.tr("Ausgabe-Einstellungen wurden inzwischen geändert; bitte neu öffnen"));
            if (revision==Long.MAX_VALUE) throw new IOException("Ausgabe-Revisionsgrenze");
            Objects.requireNonNull(preferences);
            SettingsTransaction.requireComplete(directory);
            Snapshot next=new Snapshot(revision+1,preferences);
            ConfigurationFiles.write(new File(directory,FILE_NAME),encode(next));
            return next;
        }
    }
    public static byte[] encode(Snapshot snapshot) throws IOException {
            OutputPreferences preferences=snapshot.preferences;
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try (DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_OUTPUT"); out.writeInt(11); out.writeLong(snapshot.revision); out.writeUTF(preferences.mode.name());
                out.writeInt(preferences.valueIntervalMinutes); out.writeUTF(preferences.valueTemplate);
                out.writeInt(preferences.toneVolumePercent); out.writeInt(preferences.speechVolumePercent);
                out.writeInt(preferences.valueQuietMinutes);
                out.writeInt(preferences.bellBoostDb);
                for(String word:preferences.trendSpeech.words.subList(0,5)) out.writeUTF(word);
                out.writeBoolean(preferences.tonesEnabled); out.writeBoolean(preferences.alarmSpeechEnabled); out.writeBoolean(preferences.valueSpeechEnabled); out.writeBoolean(preferences.vibrationEnabled); preferences.gap.write(out);
                out.writeUTF(preferences.speechLanguage); out.writeUTF(preferences.trendSpeech.words.get(5)); out.writeUTF(preferences.trendSpeech.words.get(6));
                out.writeUTF(preferences.gap.speechTemplate);
                // Retired schema-9 fields: retain wire compatibility, always CamAPS.
                out.writeUTF("CAMAPS"); out.writeUTF("CAMAPS");
                for(boolean enabled:preferences.trendSpeech.enabled) out.writeBoolean(enabled);
                out.writeUTF(preferences.unitMode.name());
            }
            byte[] encoded=ConfigurationFiles.seal(buffer.toByteArray()); if(encoded.length>8192) throw new IOException(AppText.tr("Ausgabe-Einstellungen zu groß")); return encoded;
    }
}
