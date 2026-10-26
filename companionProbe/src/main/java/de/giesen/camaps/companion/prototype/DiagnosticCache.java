// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Only the fixed structural formatter may populate this private, single-report cache. */
public final class DiagnosticCache {
    public static final String NAME="trend-diagnostics.txt";
    public static final String CORE_NAME="alarm-core-selftest.txt";
    public static final String EDITOR_NAME="alarm-editor-selftest.txt";
    public static final String RUNTIME_NAME="alarm-runtime-selftest.txt";
    public static synchronized void writeRuntime(File directory,String version) throws IOException {
        writeAuxiliary(directory,RUNTIME_NAME,RuntimeDiagnostics.report(version));
    }
    public static synchronized void writeEditor(File directory,String version) throws IOException {
        writeAuxiliary(directory,EDITOR_NAME,EditorDiagnostics.report(version));
    }
    public static synchronized void writeCore(File directory,String version) throws IOException {
        writeAuxiliary(directory,CORE_NAME,AlarmProbe.selfTestReport(version));
    }
    private static void writeAuxiliary(File directory,String name,String report) throws IOException {
        Path target=directory.toPath().resolve(name), temporary=directory.toPath().resolve(name+".new");
        try {
            byte[] content=report.getBytes(StandardCharsets.UTF_8);
            Files.write(temporary,content);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            Files.deleteIfExists(temporary); Files.deleteIfExists(target); throw e;
        }
    }
    public static synchronized void write(File directory,String version,TrendDiagnostics diagnostic,
                                          NotificationParser.Result result) throws IOException {
        Path target=directory.toPath().resolve(NAME), temporary=directory.toPath().resolve(NAME+".new");
        try {
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-(?:probe|beta)")) throw new IOException("Unexpected build version");
            byte[] content=("Prototyp "+version+"\n"+diagnostic.summary(result)).getBytes(StandardCharsets.UTF_8);
            if (content.length>16_384) throw new IOException("Structural report exceeds bound");
            Files.write(temporary,content);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            clear(directory);
            throw e;
        }
    }
    public static synchronized void clear(File directory) throws IOException {
        Files.deleteIfExists(directory.toPath().resolve(NAME+".new"));
        Files.deleteIfExists(directory.toPath().resolve(NAME));
    }
    private DiagnosticCache() {}
}
