// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype.alarm;

import de.giesen.camaps.companion.prototype.HistoryAnalysis;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import de.giesen.camaps.companion.prototype.alarm.AlarmStateStore.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Eight restart/storage scenarios in a dedicated cache directory; no real state/settings opened. */
public final class AlarmStateSelfTest {
    private static final long B=10000000,M=60000,E=1000000;
    private static Clock clock(long minute) { return new Clock(B+minute*M,E+minute*M,1); }
    private static HistoryAnalysis.Point point(int value,long minute) { return new HistoryAnalysis.Point(value,B+minute*M); }
    private static Rule rule(String id,Kind kind,double limit) { return new Rule(id,kind,true,limit,kind==Kind.PERSISTENT_HIGH?1:0,true,0,1440,new Outputs(false,false,3,5)); }
    private static PersistentAlarmSession session(AlarmStateStore store,Rule... rules) {
        return new PersistentAlarmSession(store,Arrays.asList(rules),new HistoryAnalysis.Limits(1,10*M,M),OutputMode.TONES_ONLY);
    }
    private static List<HistoryAnalysis.Point> rising(long minute) { return Arrays.asList(point(100,minute-3),point(103,minute-2),point(106,minute-1),point(109,minute)); }
    public static int run(File cache) throws IOException {
        Path folder=Files.createTempDirectory(cache.toPath(),"alarm-runtime-selftest-"); int passed=0;
        try {
            AlarmStateStore store=new AlarmStateStore(folder.toFile());
            if (store.load().revision==0 && store.load().entries.isEmpty()) passed++;
            Rule persistent=rule("p",Kind.PERSISTENT_HIGH,150);
            session(store,persistent).evaluate(Arrays.asList(point(159,-1),point(160,0)),clock(0),720,B-2*M,null);
            Result result=session(new AlarmStateStore(folder.toFile()),persistent).evaluate(Arrays.asList(point(160,0),point(160,1),point(161,2)),clock(2),720,B-2*M,null);
            if (result.matchedRules()==1 && store.load().entries.get("p").highSince==B) passed++;
            Rule high=rule("h",Kind.HIGH,100); PersistentAlarmSession active=session(store,high);
            active.evaluate(Arrays.asList(point(120,3)),clock(3),720,B,null); active.recordDelivered("h",clock(3)); active.snooze("h",5,clock(3));
            PersistentAlarmSession restored=session(new AlarmStateStore(folder.toFile()),high);
            Entry pause=restored.view(clock(4)).entries.get("h");
            if (pause.snoozed && pause.nextAt==B+8*M && restored.needsFreshData()) passed++;
            try { restored.recordDelivered("h",clock(9)); }
            catch (IOException expected) { passed++; }
            Clock shifted=new Clock(B+64*M,E+4*M,1);
            if (restored.view(shifted).entries.get("h").nextAt-shifted.wallMs==4*M) passed++;
            Rule rate=rule("r",Kind.RISING,2); PersistentAlarmSession first=session(store,rate);
            first.evaluate(rising(9),clock(9),720,B,null); first.recordDelivered("r",clock(9));
            PersistentAlarmSession resumed=session(store,rate); resumed.evaluate(rising(10),clock(10),720,B,null);
            boolean blocked=false; try { resumed.recordDelivered("r",clock(10)); } catch (IOException expected) { blocked=true; }
            resumed.evaluate(Arrays.asList(point(109,10),point(109,11),point(109,12),point(109,13)),clock(13),720,B,null);
            if (blocked && store.load().entries.isEmpty()) passed++;
            PersistentAlarmSession again=session(store,high); again.evaluate(Arrays.asList(point(120,14)),clock(14),720,B,null); again.recordDelivered("h",clock(14));
            session(store,rule("h",Kind.HIGH,110)).reconcile(clock(15));
            if (store.load().entries.isEmpty()) passed++;
            byte[] broken=Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)); broken[0]^=1;
            Files.write(folder.resolve(AlarmStateStore.FILE_NAME),broken);
            try { session(store,high).view(clock(16)); }
            catch (IOException expected) { if (Arrays.equals(broken,Files.readAllBytes(folder.resolve(AlarmStateStore.FILE_NAME)))) passed++; }
            return passed;
        } finally {
            Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME+".new"));
            Files.deleteIfExists(folder.resolve(AlarmStateStore.FILE_NAME)); Files.delete(folder);
        }
    }
    private AlarmStateSelfTest() {}
}
