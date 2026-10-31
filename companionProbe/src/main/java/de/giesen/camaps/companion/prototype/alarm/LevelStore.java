// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded versioned private file; atomic replacement, stale-write detection, no destructive recovery. */
public final class LevelStore {
    public static final int MAX_LEVELS=64;
    public static final int MAX_ARCHIVE_BYTES=1048576;
    public static final String FILE_NAME="alarm-levels-v1.bin";
    private static final String MAGIC="XCAM_ALARM_LEVELS";
    private static final Object LOCK=ConfigurationFiles.LOCK;
    private final File directory;
    private static byte[] digest(byte[] content) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(content); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static final class Conflict extends IOException { public Conflict() { super(AppText.tr("Alarmstufen wurden inzwischen geändert. Bitte die Liste neu öffnen.")); } }
    public static final class Snapshot {
        public final long revision;
        public final List<AlarmLevel> levels;
        public Snapshot(long revision,List<AlarmLevel> levels) {
            if (revision<0 || levels==null || levels.size()>MAX_LEVELS) throw new IllegalArgumentException(AppText.tr("Ungültige Alarmstufenliste"));
            Set<String> ids=new HashSet<>();
            for (AlarmLevel level:levels) if (level==null || !ids.add(level.rule.id)) throw new IllegalArgumentException(AppText.tr("Doppelte Stufenkennung"));
            this.revision=revision; this.levels=Collections.unmodifiableList(new ArrayList<>(levels));
        }
        public AlarmLevel find(String id) { for (AlarmLevel level:levels) if (level.rule.id.equals(id)) return level; return null; }
        public List<AlarmEngine.Rule> rules() {
            List<AlarmEngine.Rule> result=new ArrayList<>(); for (AlarmLevel level:levels) result.add(level.effectiveRule()); return Collections.unmodifiableList(result);
        }
    }
    public LevelStore(File directory) { if (directory==null) throw new IllegalArgumentException(); this.directory=directory; }
    public Snapshot load() throws IOException {
        synchronized(LOCK) {
            SettingsTransaction.recoverCore(directory);
            File target=new File(directory,FILE_NAME);
            if (!target.exists()) return new Snapshot(0,Collections.emptyList());
            return decode(ConfigurationFiles.read(target,MAX_ARCHIVE_BYTES));
        }
    }
    public static Snapshot decode(byte[] encoded) throws IOException {
            byte[] body=ConfigurationFiles.open(encoded,MAX_ARCHIVE_BYTES);
            try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(body))) {
                if (!MAGIC.equals(in.readUTF())) throw new IOException(AppText.tr("Unbekanntes Alarmstufenformat; unverändert erhalten"));
                int schema=in.readInt(); if (schema<1 || schema>5) throw new IOException(AppText.tr("Unbekanntes Alarmstufenformat; unverändert erhalten"));
                long revision=in.readLong(); int count=in.readInt();
                if (revision<1 || count<0 || count>MAX_LEVELS) throw new IOException(AppText.tr("Ungültiges Alarmstufenformat; unverändert erhalten"));
                List<AlarmLevel> levels=new ArrayList<>();
                for (int index=0;index<count;index++) {
                    String id=in.readUTF(),name=in.readUTF(); AlarmEngine.Kind kind=AlarmEngine.Kind.valueOf(in.readUTF());
                    boolean enabled=in.readBoolean(); double threshold=in.readDouble(); int duration=in.readInt();
                    boolean allDay=in.readBoolean(); int start=in.readInt(),end=in.readInt();
                    boolean tone=in.readBoolean(),speech=in.readBoolean(); int repeat=in.readInt(),snooze=in.readInt();
                    String uri=in.readUTF();
                    List<String> variants=new ArrayList<>();
                    if (schema>=2) {
                        int variantCount=in.readInt(); if (variantCount<0 || variantCount>4) throw new IOException(AppText.tr("Ungültige Sprachvarianten"));
                        for (int variant=0;variant<variantCount;variant++) variants.add(in.readUTF());
                    }
                    levels.add(new AlarmLevel(new AlarmEngine.Rule(id,kind,enabled,threshold,duration,allDay,start,end,
                            new AlarmEngine.Outputs(tone,speech,repeat,snooze)),name,uri,variants,schema>=3?emptyToNull(in.readUTF()):null,schema>=4 && in.readBoolean()));
                }
                if (in.read()!=-1) throw new IOException(AppText.tr("Unerwartete Alarmstufendaten; unverändert erhalten"));
                return new Snapshot(revision,levels);
            } catch (IllegalArgumentException e) { throw new IOException(AppText.tr("Ungültige Alarmstufendaten; unverändert erhalten"),e); }
    }
    private static String emptyToNull(String value) { return value.isEmpty()?null:value; }
    public Snapshot save(long expectedRevision,List<AlarmLevel> desired) throws IOException {
        synchronized(LOCK) {
            Snapshot current=load();
            if (current.revision!=expectedRevision) throw new Conflict();
            if (expectedRevision==Long.MAX_VALUE) throw new IOException("Revisionsgrenze erreicht");
            Snapshot next=new Snapshot(expectedRevision+1,desired);
            SettingsTransaction.requireComplete(directory);
            ConfigurationFiles.write(new File(directory,FILE_NAME),encode(next));
            return next;
        }
    }
    public static byte[] encode(Snapshot next) throws IOException {
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try (DataOutputStream out=new DataOutputStream(buffer)) {
                    out.writeUTF(MAGIC); out.writeInt(5); out.writeLong(next.revision); out.writeInt(next.levels.size());
                    for (AlarmLevel level:next.levels) {
                        AlarmEngine.Rule r=level.rule;
                        out.writeUTF(r.id); out.writeUTF(level.name); out.writeUTF(r.kind.name()); out.writeBoolean(r.enabled);
                        out.writeDouble(r.threshold); out.writeInt(r.durationMinutes); out.writeBoolean(r.allDay);
                        out.writeInt(r.startMinute); out.writeInt(r.endMinute); out.writeBoolean(r.outputs.tone); out.writeBoolean(r.outputs.speech);
                        out.writeInt(r.outputs.repeatMinutes); out.writeInt(r.outputs.snoozeMinutes); out.writeUTF(level.toneUri);
                        out.writeInt(level.speechVariants.size()); for (String variant:level.speechVariants) out.writeUTF(variant);
                        out.writeUTF(level.vibrationPattern==null?"":level.vibrationPattern); out.writeBoolean(level.suppressRecoveryRepeats);
                    }
                    out.flush();
                }
                byte[] body=buffer.toByteArray();
                if (body.length+32>MAX_ARCHIVE_BYTES) throw new IOException(AppText.tr("Alarmstufendatei zu groß"));
                return ConfigurationFiles.seal(body);
    }
}
