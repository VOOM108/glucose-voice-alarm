// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.*;
import de.giesen.camaps.companion.prototype.alarm.VibrationCatalog;

/** Alarm-usage haptics with a finite waveform, also usable without audio. */
final class PreviewVibration {
    private static PreviewVibration active;
    private final Vibrator vibrator;
    private long until;
    PreviewVibration(Context context) {
        Vibrator found=null;
        try {
            if(Build.VERSION.SDK_INT>=31) { VibratorManager manager=context.getSystemService(VibratorManager.class); if(manager!=null) found=manager.getDefaultVibrator(); }
            else found=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch(RuntimeException ignored) { }
        vibrator=found;
    }
    boolean available() { try { return vibrator!=null && vibrator.hasVibrator(); } catch(RuntimeException e) { return false; } }
    boolean start(String pattern) {
        return startPatterns(pattern==null?java.util.Collections.emptyList():java.util.Collections.singletonList(pattern));
    }
    boolean startPatterns(java.util.List<String> patterns) {
        stop(); if(!available() || patterns.isEmpty() || patterns.size()>2) return false;
        try {
            java.util.List<Long> sequence=new java.util.ArrayList<>(); long duration=0;
            for(int i=0;i<patterns.size();i++) { long[] times=VibrationCatalog.valueOf(patterns.get(i)).timings(); if(i>0) times[0]=180; for(long time:times) { sequence.add(time); duration+=time; } }
            long[] times=new long[sequence.size()]; for(int i=0;i<times.length;i++) times[i]=sequence.get(i);
            VibrationEffect effect=VibrationEffect.createWaveform(times,-1);
            if(Build.VERSION.SDK_INT>=33) vibrator.vibrate(effect,new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build());
            else vibrator.vibrate(effect,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            active=this; until=SystemClock.elapsedRealtime()+duration; return true;
        } catch(RuntimeException e) { stop(); return false; }
    }
    boolean busy() { return until>SystemClock.elapsedRealtime(); }
    void stop() { until=0; if(active==this) { active=null; if(vibrator!=null) try { vibrator.cancel(); } catch(RuntimeException ignored) { } } }
}
