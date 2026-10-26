// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private runtime state only: no glucose, names, raw notifications, tones or credentials. */
public final class AlarmStateStore {
    public static final String FILE_NAME="alarm-runtime-v1.bin";
    private static final Object LOCK=new Object();
    private final File directory;
    public static final class Conflict extends IOException { public Conflict() { super(AppText.tr("Alarmzustände wurden inzwischen geändert")); } }
    public static final class ClockBlocked extends IOException { public ClockBlocked() { super(AppText.tr("Uhrzeit muss neu geprüft werden; Zustände unverändert")); } }
    public static final class Clock {
        public final long wallMs,elapsedMs,boot;
        public Clock(long wallMs,long elapsedMs,long boot) {
            if (wallMs<=0 || elapsedMs<0 || boot< -1) throw new IllegalArgumentException("Invalid clock");
            this.wallMs=wallMs; this.elapsedMs=elapsedMs; this.boot=boot;
        }
    }
    public static final class Entry {
        public final String id,fingerprint;
        public final AlarmEngine.Kind kind;
        public final long highSince,startedAt,nextAt,lastDeliveredAt;
        public final boolean active,snoozed,acceptedInEpisode;
        public Entry(String id,String fingerprint,AlarmEngine.Kind kind,long highSince,boolean active,boolean snoozed,
                     long startedAt,long nextAt,long lastDeliveredAt) {
            this(id,fingerprint,kind,highSince,active,snoozed,startedAt,nextAt,lastDeliveredAt,false);
        }
        public Entry(String id,String fingerprint,AlarmEngine.Kind kind,long highSince,boolean active,boolean snoozed,long startedAt,long nextAt,long lastDeliveredAt,boolean acceptedInEpisode) {
            if(acceptedInEpisode && !active) throw new IllegalArgumentException("Accepted inactive alarm");
            this.acceptedInEpisode=acceptedInEpisode;
            if (id==null || !id.matches("[A-Za-z0-9_-]{1,64}") || fingerprint==null || !fingerprint.matches("[a-f0-9]{64}")
                    || kind==null || highSince<0 || startedAt<0 || nextAt<0 || lastDeliveredAt<0
                    || (kind!=AlarmEngine.Kind.PERSISTENT_HIGH && highSince!=0)
                    || (snoozed && (!active || !LevelForm.hasSnooze(kind)))
                    || (active && (startedAt==0 || nextAt==0 || lastDeliveredAt==0))
                    || (!active && (snoozed || startedAt!=0 || nextAt!=0 || lastDeliveredAt!=0)))
                throw new IllegalArgumentException("Invalid alarm state");
            this.id=id; this.fingerprint=fingerprint; this.kind=kind; this.highSince=highSince;
            this.active=active; this.snoozed=snoozed; this.startedAt=startedAt; this.nextAt=nextAt; this.lastDeliveredAt=lastDeliveredAt;
        }
        public static Entry idle(AlarmEngine.Rule rule,long highSince) {
            return new Entry(rule.id,fingerprint(rule),rule.kind,highSince,false,false,0,0,0);
        }
        public Entry withTimer(long timer) { return new Entry(id,fingerprint,kind,timer,active,snoozed,startedAt,nextAt,lastDeliveredAt,acceptedInEpisode); }
        public Entry clearActive() { return new Entry(id,fingerprint,kind,highSince,false,false,0,0,0); }
        public Entry accepted(boolean accepted) { return new Entry(id,fingerprint,kind,highSince,active,snoozed,startedAt,nextAt,lastDeliveredAt,accepted); }
        Entry shift(long delta) {
            return new Entry(id,fingerprint,kind,shiftTime(highSince,delta),active,snoozed,
                    shiftTime(startedAt,delta),shiftTime(nextAt,delta),shiftTime(lastDeliveredAt,delta),acceptedInEpisode);
        }
        private static long shiftTime(long value,long delta) {
            if (value==0) return 0;
            long shifted=Math.addExact(value,delta);
            if (shifted<=0) throw new IllegalArgumentException("Invalid rebased time"); return shifted;
        }
    }
    public static final class Snapshot {
        public final long revision;
        public final Clock clock;
        public final Map<String,Entry> entries;
        public Snapshot(long revision,Clock clock,Collection<Entry> values) {
            if (revision<0 || values==null || values.size()>LevelStore.MAX_LEVELS || (revision>0 && clock==null))
                throw new IllegalArgumentException("Invalid runtime snapshot");
            Map<String,Entry> rows=new LinkedHashMap<>(); int thresholdActive=0;
            for (Entry value:values) {
                if (value==null || rows.put(value.id,value)!=null) throw new IllegalArgumentException("Duplicate runtime id");
                if (value.active && LevelForm.hasSnooze(value.kind)) thresholdActive++;
                if (clock!=null && (value.highSince>clock.wallMs || value.startedAt>clock.wallMs || value.lastDeliveredAt>clock.wallMs))
                    throw new IllegalArgumentException("Future runtime origin");
            }
            if (thresholdActive>1) throw new IllegalArgumentException("Multiple threshold alarms active");
            this.revision=revision; this.clock=clock; entries=Collections.unmodifiableMap(rows);
        }
    }
    public AlarmStateStore(File directory) { if (directory==null) throw new IllegalArgumentException(); this.directory=directory; }
    public static String fingerprint(AlarmEngine.Rule rule) {
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(buffer);
            out.writeUTF("xDrip-805ba04-runtime-v1"); out.writeUTF(rule.id); out.writeUTF(rule.kind.name());
            out.writeDouble(rule.threshold); out.writeInt(rule.durationMinutes); out.writeBoolean(rule.allDay);
            out.writeInt(rule.startMinute); out.writeInt(rule.endMinute); out.flush();
            StringBuilder result=new StringBuilder(); for (byte value:digest(buffer.toByteArray())) result.append(String.format(Locale.ROOT,"%02x",value & 255));
            return result.toString();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Snapshot load() throws IOException {
        synchronized(LOCK) {
            Path target=new File(directory,FILE_NAME).toPath();
            if (!Files.exists(target)) return new Snapshot(0,null,Collections.emptyList());
            if (Files.size(target)>65536) throw new IOException(AppText.tr("Alarmzustandsdatei zu groß; unverändert erhalten"));
            byte[] encoded=Files.readAllBytes(target);
            if (encoded.length<32) throw new IOException(AppText.tr("Unvollständige Alarmzustandsdatei"));
            byte[] body=Arrays.copyOf(encoded,encoded.length-32);
            if (!MessageDigest.isEqual(digest(body),Arrays.copyOfRange(encoded,encoded.length-32,encoded.length)))
                throw new IOException(AppText.tr("Prüfsummenfehler; Alarmzustände unverändert erhalten"));
            try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(body))) {
                if (!"XCAM_ALARM_RUNTIME".equals(in.readUTF())) throw new IOException(AppText.tr("Unbekanntes Alarmzustandsformat"));
                int schema=in.readInt(); if(schema<1 || schema>2) throw new IOException(AppText.tr("Unbekanntes Alarmzustandsformat"));
                long revision=in.readLong(); Clock clock=new Clock(in.readLong(),in.readLong(),in.readLong());
                int count=in.readInt(); if (revision<1 || count<0 || count>LevelStore.MAX_LEVELS) throw new IOException(AppText.tr("Ungültige Alarmzustandsliste"));
                List<Entry> rows=new ArrayList<>();
                for (int i=0;i<count;i++) rows.add(new Entry(in.readUTF(),in.readUTF(),AlarmEngine.Kind.valueOf(in.readUTF()),
                        in.readLong(),in.readBoolean(),in.readBoolean(),in.readLong(),in.readLong(),in.readLong(),schema>=2 && in.readBoolean()));
                if (in.read()!=-1) throw new IOException(AppText.tr("Zusätzliche Alarmzustandsdaten"));
                return new Snapshot(revision,clock,rows);
            } catch (IllegalArgumentException e) { throw new IOException(AppText.tr("Ungültige Alarmzustände; unverändert erhalten"),e); }
        }
    }
    public Snapshot save(long revision,Clock clock,Collection<Entry> entries) throws IOException {
        synchronized(LOCK) {
            Snapshot before=load(); if (before.revision!=revision) throw new Conflict();
            if (revision==Long.MAX_VALUE) throw new IOException("Runtime revision overflow");
            Snapshot next=new Snapshot(revision+1,clock,entries);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try (DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_ALARM_RUNTIME"); out.writeInt(2); out.writeLong(next.revision);
                out.writeLong(clock.wallMs); out.writeLong(clock.elapsedMs); out.writeLong(clock.boot); out.writeInt(next.entries.size());
                for (Entry row:next.entries.values()) {
                    out.writeUTF(row.id); out.writeUTF(row.fingerprint); out.writeUTF(row.kind.name()); out.writeLong(row.highSince);
                    out.writeBoolean(row.active); out.writeBoolean(row.snoozed); out.writeLong(row.startedAt); out.writeLong(row.nextAt); out.writeLong(row.lastDeliveredAt); out.writeBoolean(row.acceptedInEpisode);
                }
            }
            Path target=new File(directory,FILE_NAME).toPath(),temporary=new File(directory,FILE_NAME+".new").toPath();
            Files.createDirectories(directory.toPath());
            try {
                byte[] body=buffer.toByteArray();
                try (FileOutputStream file=new FileOutputStream(temporary.toFile())) { file.write(body); file.write(digest(body)); file.getFD().sync(); }
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                return next;
            } finally { Files.deleteIfExists(temporary); }
        }
    }
    /** Filter by current condition identity; a rename/tone change does not cancel an existing pause. */
    public static Snapshot restore(Snapshot stored,List<AlarmEngine.Rule> rules,Clock now) throws ClockBlocked {
        Map<String,AlarmEngine.Rule> current=new HashMap<>(); for (AlarmEngine.Rule rule:rules) if (rule.enabled) current.put(rule.id,rule);
        long shift=0;
        if (stored.clock!=null) {
            try {
                if (now.boot>=0 && now.boot==stored.clock.boot) {
                    if (now.elapsedMs<stored.clock.elapsedMs) throw new ClockBlocked();
                    shift=Math.subtractExact(Math.subtractExact(now.wallMs,stored.clock.wallMs),Math.subtractExact(now.elapsedMs,stored.clock.elapsedMs));
                } else if (now.wallMs<stored.clock.wallMs) throw new ClockBlocked();
            } catch (ArithmeticException e) { throw new ClockBlocked(); }
        }
        List<Entry> retained=new ArrayList<>();
        try {
            for (Entry row:stored.entries.values()) {
                AlarmEngine.Rule rule=current.get(row.id);
                if (rule!=null && rule.kind==row.kind && fingerprint(rule).equals(row.fingerprint)) {
                    Entry restored=row.shift(shift);
                    if(stored.clock!=null && now.boot!=stored.clock.boot) restored=restored.accepted(false);
                    retained.add(restored);
                }
            }
            return new Snapshot(stored.revision,now,retained);
        } catch (IllegalArgumentException | ArithmeticException e) { throw new ClockBlocked(); }
    }
}
