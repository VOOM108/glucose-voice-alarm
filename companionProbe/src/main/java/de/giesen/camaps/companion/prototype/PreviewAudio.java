// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import de.giesen.camaps.companion.prototype.alarm.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Shared preview/live audio adapter. Real output additionally requires the service's explicit permission gate. */
final class PreviewAudio implements AlarmOutputFlow.Port {
    interface Status { void show(String message); }
    private static final List<WeakReference<PreviewAudio>> OWNERS=new ArrayList<>();
    private final Context context;
    private final PreviewVibration vibration;
    private final Status status;
    private final BooleanSupplier livePermission;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private Ringtone ringtone;
    private MediaPlayer bell;
    private Runnable afterTone,drainBell;
    private boolean ready,closed;
    private long revision=-1;
    private long speechGeneration;
    private OutputPreferences preferences;
    private long busyUntil;
    private long requestSequence;
    private String activeSpeechId;
    private AlarmOutputFlow.Plan pendingPlan;
    private String previewSpeechLanguage;
    void previewLanguage(String language) {
        if(!"de".equals(language) && !"en".equals(language)) throw new IllegalArgumentException();
        if(!language.equals(previewSpeechLanguage)) { stop(); releaseSpeech(); previewSpeechLanguage=language; }
        prepareSpeech();
    }
    private final Runnable stopTone=() -> { stopTone(); runAfterTone(); };
    private final Runnable bellTimeout=() -> timeoutBell();
    private final Runnable stopSpeech=() -> stop();
    PreviewAudio(Context context,Status status) {
        this(context,status,() -> false);
    }
    PreviewAudio(Context context,Status status,BooleanSupplier livePermission) {
        this.context=context.getApplicationContext(); this.status=status; vibration=new PreviewVibration(this.context);
        this.livePermission=livePermission;
        OWNERS.removeIf(reference -> reference.get()==null); OWNERS.add(new WeakReference<>(this));
    }
    /** Called after an explicit settings save. Changes cancel all pending/current previews. */
    static void configurationChanged() {
        for (WeakReference<PreviewAudio> reference:new ArrayList<>(OWNERS)) {
            PreviewAudio owner=reference.get(); if (owner!=null && !owner.closed) owner.invalidate();
        }
        OWNERS.removeIf(reference -> { PreviewAudio owner=reference.get(); return owner==null || owner.closed; });
    }
    private void invalidate() { stop(); releaseSpeech(); revision=-1; preferences=null; }
    private boolean refresh() {
        if (closed || !SafetyConsent.ready(context)) {stop();return false;}
        try {
            OutputPreferenceStore.Snapshot snapshot=new OutputPreferenceStore(context.getFilesDir()).load();
            if (snapshot.revision!=revision) { stop(); releaseSpeech(); preferences=snapshot.preferences; revision=snapshot.revision; }
            return true;
        } catch (IOException | RuntimeException e) {
            invalidate(); status.show(AppText.tr("Ausgabe-Einstellungen nicht lesbar. Vorschau gesperrt; gespeicherte Datei bleibt erhalten.")); return false;
        }
    }
    /** One audible owner in this process. Live alarms take priority over manual previews. */
    private boolean takeOutput() {
        List<PreviewAudio> others=new ArrayList<>();
        for(WeakReference<PreviewAudio> reference:new ArrayList<>(OWNERS)) {
            PreviewAudio owner=reference.get(); if(owner!=null && owner!=this && !owner.closed && owner.busy()) others.add(owner);
        }
        boolean live=livePermission.getAsBoolean();
        for(PreviewAudio owner:others) if(owner.livePermission.getAsBoolean() && !live) {
            status.show(AppText.tr("Live-Ausgabe läuft; Vorschau danach erneut starten.")); return false;
        }
        for(PreviewAudio owner:others) owner.stop();
        return true;
    }
    boolean speechPermitted() {
        if (!refresh()) return false;
        if (!preferences.speechAllowed()) { status.show(AppText.tr("Sprache ist ausgeschaltet. Änderungen bitte zuerst speichern.")); return false; }
        return true;
    }
    void prepareSpeech() {
        if (!speechPermitted() || tts!=null) return;
        status.show(AppText.tr("Offline-Stimme wird vorbereitet …"));
        final String language=previewSpeechLanguage==null?preferences.speechLanguage:previewSpeechLanguage;
        final long generation=++speechGeneration;
        try {
            tts=new TextToSpeech(context,result -> handler.post(() -> {
                if (closed || tts==null || generation!=speechGeneration) return;
                ready=false;
                try {
                    Set<Voice> voices=result==TextToSpeech.SUCCESS?tts.getVoices():null;
                    if (voices!=null) for (Voice voice:voices) {
                        if (language.equals(voice.getLocale().getLanguage()) && !voice.isNetworkConnectionRequired()
                                && tts.setVoice(voice)==TextToSpeech.SUCCESS) { ready=true; break; }
                    }
                } catch (RuntimeException e) { ready=false; }
                if (ready) {
                    tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { }
                        @Override public void onDone(String id) { handler.post(() -> { if (id!=null && id.equals(activeSpeechId)) { busyUntil=0; activeSpeechId=null; } }); }
                        @Override public void onError(String id) { onDone(id); }
                    });
                }
                status.show(ready?AppText.tr("Offline-Stimme bereit. Vorschauen starten nur auf Knopfdruck."):
                        AppText.tr("Keine passende Offline-Stimme bereit. Bitte Androids Sprachausgabe prüfen."));
            }));
        } catch (RuntimeException e) { releaseSpeech(); status.show(AppText.tr("Sprachausgabe konnte nicht vorbereitet werden.")); }
    }
    boolean speak(String text) {
        if (!refresh()) return false;
        return speak(text,preferences.speechVolumePercent);
    }
    boolean speak(String text,int volumePercent) { return speak(text,volumePercent,false); }
    private boolean speak(String text,int volumePercent,boolean preserveVibration) {
        if (!speechPermitted()) return false;
        if (!takeOutput()) return false;
        if(preserveVibration) stopAudio(); else stop();
        if (volumePercent<1 || volumePercent>100) { status.show(AppText.tr("Sprachlautstärke ist stumm oder ungültig.")); return false; }
        if (tts==null) prepareSpeech();
        if (!ready || tts==null) { status.show(AppText.tr("Noch keine Ansage: Offline-Stimme wird vorbereitet oder fehlt. Danach bitte erneut auf Anhören tippen.")); return false; }
        try {
            activeSpeechId="preview-"+(++requestSequence);
            Bundle parameters=new Bundle(); parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,volumePercent/100f);
            int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,parameters,activeSpeechId);
            if (result!=TextToSpeech.SUCCESS) { status.show(AppText.tr("Sprachausgabe hat den Text abgelehnt.")); return false; }
            handler.postDelayed(stopSpeech,90000);
            busyUntil=SystemClock.elapsedRealtime()+90000;
            status.show(AppText.tr("Vorschautext an die Sprachausgabe übergeben.")); return true;
        } catch (RuntimeException e) { stop(); status.show(AppText.tr("Sprachvorschau konnte nicht gestartet werden.")); return false; }
    }
    boolean tone(String uri) {
        if (!refresh()) return false;
        return tone(uri,preferences.toneVolumePercent);
    }
    boolean tone(String uri,int volumePercent) {
        if (!refresh()) return false;
        return tone(uri,volumePercent,preferences.bellBoostDb);
    }
    boolean tone(String uri,int volumePercent,int boostDb) {
        return tone(uri,volumePercent,boostDb,false);
    }
    private boolean tone(String uri,int volumePercent,int boostDb,boolean preserveVibration) {
        if (!refresh()) return false;
        if (!takeOutput()) return false;
        if(preserveVibration) stopAudio(); else stop();
        if (volumePercent<1 || volumePercent>100) { status.show(AppText.tr("Tonlautstärke ist stumm oder ungültig.")); return false; }
        if (Build.VERSION.SDK_INT<28 && volumePercent!=100) { status.show(AppText.tr("Eigene Tonlautstärke erfordert Android 9 oder neuer.")); return false; }
        if (boostDb<0 || boostDb>ToneCatalog.MAX_BOOST_DB) { status.show(AppText.tr("Ungültiger Glockenton-Boost.")); return false; }
        try {
            if (ToneCatalog.setOf(uri)!=null) {
                bell=new MediaPlayer(); final MediaPlayer player=bell;
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                int id=context.getResources().getIdentifier(Uri.parse(uri).getLastPathSegment(),"raw",context.getPackageName());
                try (AssetFileDescriptor asset=context.getResources().openRawResourceFd(id)) {
                    if (asset==null) throw new IOException("Missing local tone");
                    player.setDataSource(asset.getFileDescriptor(),asset.getStartOffset(),asset.getLength());
                    player.prepare();
                }
                float gain=ToneCatalog.playbackGain(volumePercent,boostDb);
                player.setLooping(false); player.setVolume(gain,gain);
                player.setOnCompletionListener(completed -> {
                    if (closed || bell!=player || drainBell!=null) return;
                    handler.removeCallbacks(bellTimeout);
                    // Let the final PCM buffer drain before releasing or starting speech.
                    drainBell=() -> {
                        drainBell=null;
                        if (bell!=player || closed) return;
                        bell=null; player.release(); busyUntil=0; runAfterTone();
                    };
                    handler.postDelayed(drainBell,120);
                });
                player.setOnErrorListener((failed,what,extra) -> {
                    if (bell==player) { stop(); status.show(AppText.tr("App-Ton konnte nicht vollständig abgespielt werden.")); }
                    return true;
                });
                player.start(); handler.postDelayed(bellTimeout,8000);
                status.show(AppText.tr("App-Glockenton wird vollständig abgespielt.")); return true;
            }
            ringtone=RingtoneManager.getRingtone(context,Uri.parse(uri));
            if (ringtone==null) throw new IllegalStateException();
            ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            if (Build.VERSION.SDK_INT>=28) { ringtone.setLooping(false); ringtone.setVolume(volumePercent/100f); }
            long duration=8000; // Only external Android tones use a fixed cap.
            ringtone.play(); handler.postDelayed(stopTone,duration);
            busyUntil=SystemClock.elapsedRealtime()+duration;
            status.show(ToneCatalog.setOf(uri)==null?AppText.tr("Tonvorschau an Android übergeben; spätestens nach acht Sekunden beendet."):AppText.tr("Kurzer App-Glockenton an Android übergeben.")); return true;
        } catch (IOException | RuntimeException e) { stop(); status.show(AppText.tr("Der Ton konnte nicht gestartet werden. Bitte einen anderen auswählen.")); return false; }
    }
    boolean toneThen(String uri,Runnable finished) {
        if (!tone(uri)) return false;
        afterTone=finished; return true;
    }
    private void runAfterTone() { Runnable next=afterTone; afterTone=null; if (!closed && SafetyConsent.ready(context) && next!=null) next.run(); }
    private void timeoutBell() { stop(); status.show(AppText.tr("Tonwiedergabe hat nicht regulär geendet und wurde beendet.")); }
    private void stopTone() {
        handler.removeCallbacks(stopTone);
        handler.removeCallbacks(bellTimeout);
        if (drainBell!=null) { handler.removeCallbacks(drainBell); drainBell=null; }
        if (bell!=null) { try { bell.release(); } catch (RuntimeException e) { /* Best-effort release. */ } bell=null; }
        if (ringtone!=null) { try { ringtone.stop(); } catch (RuntimeException e) { /* Best-effort release. */ } ringtone=null; }
    }
    @Override public void stop() { pendingPlan=null; vibration.stop(); stopAudio(); }
    private void stopAudio() {
        busyUntil=0; activeSpeechId=null;
        afterTone=null;
        stopTone(); handler.removeCallbacks(stopSpeech);
        if (tts!=null) try { tts.stop(); } catch (RuntimeException e) { /* Best-effort release. */ }
    }
    @Override public boolean available(AlarmOutputFlow.Plan plan) {
        if (plan==null || (!plan.synthetic && !livePermission.getAsBoolean()) || !refresh()) return false;
        if (plan.speech!=null) {
            if (!speechPermitted()) return false;
            if (!ready) {
                prepareSpeech();
                status.show(plan.synthetic?AppText.tr("Stimme wird vorbereitet. Sprachvorschauen bitte danach erneut starten."):AppText.tr("Offline-Stimme noch nicht bereit; ein ausgewählter Ton bleibt verfügbar."));
                if (plan.toneUri==null && !(plan.vibrationPattern!=null && vibration.available())) return false;
            }
        }
        return plan.toneUri!=null || (plan.speech!=null && ready) || (plan.vibrationPattern!=null && vibration.available());
    }
    @Override public boolean start(AlarmOutputFlow.Plan plan) {
        if (!available(plan)) return false;
        if (!takeOutput()) return false;
        stop();
        pendingPlan=plan;
        boolean audioStarted=false;
        if (!plan.toneUris.isEmpty()) audioStarted=playTones(plan.toneUris,0,plan.speech==null?null:() -> {
            AlarmOutputFlow.Plan current=pendingPlan;
            if(current!=null && current.speech!=null) speak(current.speech,preferences.speechVolumePercent,true);
        });
        else if(plan.speech!=null && ready) audioStarted=speak(plan.speech);
        boolean vibrationStarted=!plan.vibrationPatterns.isEmpty() && vibration.startPatterns(plan.vibrationPatterns);
        return audioStarted || vibrationStarted;
    }
    boolean refreshPending(AlarmOutputFlow.Plan plan) {
        if(plan==null || pendingPlan==null || activeSpeechId!=null || afterTone==null || !pendingPlan.ruleIds.containsAll(plan.ruleIds)) return false;
        pendingPlan=plan; return true;
    }
    private boolean playTones(List<String> tones,int index,Runnable finished) {
        if(!tone(tones.get(index),preferences.toneVolumePercent,preferences.bellBoostDb,true)) return false;
        afterTone=index+1<tones.size()?() -> playTones(tones,index+1,finished):finished; return true;
    }
    boolean vibrate(String pattern) { if(!SafetyConsent.ready(context) || !takeOutput()) return false; stop(); boolean accepted=vibration.start(pattern); status.show(accepted?AppText.tr("Vibrationsvorschau gestartet."):AppText.tr("Keine Vibration verfügbar.")); return accepted; }
    void pause() { stop(); releaseSpeech(); }
    boolean busy() { return vibration.busy() || bell!=null || busyUntil>SystemClock.elapsedRealtime() || afterTone!=null; }
    private void releaseSpeech() {
        ready=false; speechGeneration++;
        if (tts!=null) { try { tts.stop(); tts.shutdown(); } catch (RuntimeException e) { /* Best-effort release. */ } tts=null; }
    }
    void close() { stop(); closed=true; releaseSpeech(); handler.removeCallbacksAndMessages(null); OWNERS.removeIf(reference -> reference.get()==null || reference.get()==this); }
}
