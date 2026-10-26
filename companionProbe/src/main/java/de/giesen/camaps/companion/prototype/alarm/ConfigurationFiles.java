// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;
import de.giesen.camaps.companion.prototype.AppText;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** One process-wide configuration lock; private bounded/checksummed files only. */
public final class ConfigurationFiles {
    public static final Object LOCK=new Object();
    public static byte[] seal(byte[] body) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); out.write(body); out.write(hash(body)); return out.toByteArray();
    }
    private static byte[] hash(byte[] body) { try { return MessageDigest.getInstance("SHA-256").digest(body); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    public static byte[] open(byte[] data,int limit) throws IOException {
        if(data.length<32 || data.length>limit) throw new IOException(AppText.tr("Einstellungsdatei unvollständig oder zu groß; unverändert erhalten"));
        byte[] body=Arrays.copyOf(data,data.length-32);
        if(!MessageDigest.isEqual(hash(body),Arrays.copyOfRange(data,data.length-32,data.length))) throw new IOException(AppText.tr("Prüfsummenfehler; Datei unverändert erhalten"));
        return body;
    }
    public static byte[] read(File file,int limit) throws IOException {
        if(file.length()>limit) throw new IOException(AppText.tr("Einstellungsdatei zu groß; unverändert erhalten")); return Files.readAllBytes(file.toPath());
    }
    public static void write(File file,byte[] data) throws IOException {
        Files.createDirectories(file.getParentFile().toPath()); Path temp=new File(file.getPath()+".new").toPath();
        try {
            try(FileOutputStream out=new FileOutputStream(temp.toFile())) { out.write(data); out.getFD().sync(); }
            Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private ConfigurationFiles() {}
}
