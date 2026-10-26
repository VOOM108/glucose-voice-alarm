// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.io.*;
import java.util.*;

/** Five local slots, checksummed and revision checked. No export/import surface. */
public final class ProfileStore {
    public static final int SLOTS=5,MAX_BYTES=SLOTS*(ConfigurationSnapshot.MAX_BYTES+256)+256;
    public static final String FILE_NAME="settings-profiles-v1.bin";
    private final File directory;
    public ProfileStore(File directory) { this.directory=Objects.requireNonNull(directory); }
    public static final class Entry {
        public final String name;
        public final ConfigurationSnapshot settings;
        public Entry(String name,ConfigurationSnapshot settings) {
            if(name==null || name.trim().isEmpty() || name.length()>40 || name.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Profilname: 1 bis 40 Zeichen");
            this.name=name.trim(); this.settings=Objects.requireNonNull(settings);
        }
    }
    public static final class Snapshot {
        public final long revision;
        public final List<Entry> slots;
        private Snapshot(long revision,List<Entry> slots) { this.revision=revision; this.slots=Collections.unmodifiableList(slots); }
    }
    public Snapshot load() throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            File file=new File(directory,FILE_NAME);
            if(!file.exists()) return new Snapshot(0,new ArrayList<>(Collections.nCopies(SLOTS,null)));
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(ConfigurationFiles.open(ConfigurationFiles.read(file,MAX_BYTES),MAX_BYTES)))) {
                if(!"XCAM_PROFILES".equals(in.readUTF()) || in.readInt()!=1) throw new IOException(AppText.tr("Unbekanntes Profilformat"));
                long revision=in.readLong(); if(revision<1) throw new IOException(AppText.tr("Ungültige Profilrevision")); List<Entry> entries=new ArrayList<>();
                for(int i=0;i<SLOTS;i++) entries.add(in.readBoolean()?new Entry(in.readUTF(),ConfigurationSnapshot.decode(ConfigurationSnapshot.bytes(in,ConfigurationSnapshot.MAX_BYTES))):null);
                if(in.read()!=-1) throw new IOException(AppText.tr("Zusätzliche Profildaten")); return new Snapshot(revision,entries);
            } catch(IllegalArgumentException e) { throw new IOException(AppText.tr("Ungültige Profildaten; unverändert erhalten"),e); }
        }
    }
    public Snapshot save(long expectedRevision,int slot,Entry entry) throws IOException {
        synchronized(ConfigurationFiles.LOCK) {
            if(slot<0 || slot>=SLOTS) throw new IllegalArgumentException(AppText.tr("Ungültiger Profilplatz")); Snapshot current=load();
            if(current.revision!=expectedRevision) throw new IOException(AppText.tr("Profile wurden inzwischen geändert. Bitte neu öffnen."));
            if(current.revision==Long.MAX_VALUE) throw new IOException("Profil-Revisionsgrenze erreicht");
            List<Entry> entries=new ArrayList<>(current.slots); entries.set(slot,entry);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(buffer)) {
                out.writeUTF("XCAM_PROFILES"); out.writeInt(1); out.writeLong(expectedRevision+1);
                for(Entry value:entries) { out.writeBoolean(value!=null); if(value!=null) { out.writeUTF(value.name); ConfigurationSnapshot.bytes(out,value.settings.encode()); } }
            }
            byte[] encoded=ConfigurationFiles.seal(buffer.toByteArray()); if(encoded.length>MAX_BYTES) throw new IOException(AppText.tr("Profildatei zu groß"));
            ConfigurationFiles.write(new File(directory,FILE_NAME),encoded); return new Snapshot(expectedRevision+1,entries);
        }
    }
}
