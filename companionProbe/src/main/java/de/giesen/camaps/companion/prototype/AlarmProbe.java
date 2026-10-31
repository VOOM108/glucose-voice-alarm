// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import de.giesen.camaps.companion.prototype.alarm.AlarmEngine;
import de.giesen.camaps.companion.prototype.alarm.AlarmEngine.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Live input reaches an explicitly inactive engine. Synthetic checks use entirely separate instances. */
public final class AlarmProbe {
    public enum Ingress { WAITING, INACTIVE, NO_VALUE, DUPLICATE, OFF }
    private static final AlarmEngine LIVE=AlarmEngine.inactive();
    public static volatile Ingress ingress=Ingress.WAITING;
    public static volatile int selfTestPassed=-1;
    public static void receive(Number mgdl,long nowWall,int minuteOfDay) {
        if (mgdl==null) { ingress=Ingress.NO_VALUE; LIVE.reset(); return; }
        // Neither posted nor received time may be renamed to a verified measurement time.
        LIVE.evaluate(Arrays.asList(new HistoryAnalysis.Point(mgdl.doubleValue(),null)),nowWall,minuteOfDay,null,
                result -> ingress=Ingress.INACTIVE);
    }
    public static void clear(Ingress state) { LIVE.reset(); ingress=state; }
    public static int runSyntheticSelfTest() {
        final long base=10000000, minute=60000;
        int passed=0;
        for (Kind kind:new Kind[]{Kind.HIGH,Kind.LOW,Kind.PERSISTENT_HIGH,Kind.RISING,Kind.FALLING}) {
            double threshold=kind==Kind.RISING || kind==Kind.FALLING?2:100;
            Rule rule=new Rule("synthetic",kind,true,threshold,1,true,0,1440,new Outputs(false,false,1,1));
            AlarmEngine engine=new AlarmEngine(Arrays.asList(rule),new HistoryAnalysis.Limits(1,2*minute,minute),OutputMode.TONES_ONLY);
            List<HistoryAnalysis.Point> data=new ArrayList<>();
            int[] values=kind==Kind.FALLING?new int[]{109,106,103,100}:
                    kind==Kind.RISING?new int[]{100,103,106,109}:kind==Kind.LOW?new int[]{80}:new int[]{120};
            if (kind==Kind.PERSISTENT_HIGH) {
                engine.evaluate(Arrays.asList(new HistoryAnalysis.Point(119,base-minute),new HistoryAnalysis.Point(120,base)),base,720,base-2*minute,null);
                values=new int[]{120,121,122};
            }
            for (int i=0;i<values.length;i++) data.add(new HistoryAnalysis.Point(values[i],base+i*minute));
            Result result=engine.evaluate(data,base+(values.length-1)*minute,720,base-2*minute,null);
            if (result.status==Status.EVALUATED && result.matchedRules()==1 && !result.platformDeliveryEnabled) passed++;
        }
        return passed;
    }
    public static String selfTestReport(String version) {
        if (version==null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-(?:probe|beta)")) throw new IllegalArgumentException("Invalid build version");
        int passed=selfTestPassed;
        if (passed< -1 || passed>5) throw new IllegalStateException("Invalid self-test status");
        return "Prototyp "+version+"\nAlarmkern: xDrip 805ba04\nRegelmethoden: 23\nSynthetische Alarmarten: "
                +(passed<0?"ausstehend":passed+"/5")+"\nLive-Anbindung: "+ingress.name()
                +"\nPlattformausgabe: deaktiviert\nSelbsttest-Daten: ausschliesslich synthetisch\n";
    }
    private AlarmProbe() {}
}
