// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import androidx.activity.ComponentActivity;
import android.os.*;
import android.graphics.Canvas;
import android.view.View;

/** Round display and optional phone-alert relay with explicit local safety gates. */
public final class WatchActivity extends ComponentActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private View face;
    private boolean demo;
    private boolean ambientPreview;
    private final Runnable refresh=new Runnable(){public void run(){if(face!=null)face.invalidate();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);if(WatchConsent.ready(this))showFace();else consent();}
    private void consent(){
        WatchAlerts.set(this,false);
        face=null;final int step=WatchConsent.step(this);
        String title=WatchConsent.text(step==1?"Wichtiger Hinweis":"Nutzungsbedingungen",step==1?"Important warning":"Terms of use");
        String warning=step==1?WatchConsent.text("Experimentelles Forschungswerkzeug. Nicht für medizinische Verwendung, kein zertifiziertes Medizinprodukt. Werte, Anzeige und übernommene Telefonalarme können falsch, verspätet oder ausgefallen sein. Keine Therapie- oder Dosierungsentscheidung darauf stützen. Die Uhr berechnet keine eigenen Glukosealarme; ohne Verbindung können Telefonalarme fehlen.",
                "Experimental research tool. Not for medical use; not a certified medical device. Readings, display and relayed phone alerts may be incorrect, delayed or unavailable. Do not base treatment or dosing decisions on them. The watch does not calculate glucose alarms; phone alerts may be missed without a connection."):
                WatchConsent.text("Nutzung auf eigene Verantwortung. Die Anzeige ersetzt weder CamAPS noch zugelassene Mess- oder Alarmgeräte. Keine Gewähr im Rahmen des geltenden Rechts. GPLv3-Rechte und zwingende gesetzliche Rechte bleiben erhalten. Den vollständigen GPLv3-Text kannst Du unten lesen.",
                "Use at your own risk. This display does not replace CamAPS or approved monitoring or alarm devices. No warranty to the extent permitted by law. GPLv3 rights and mandatory legal rights remain unchanged. The complete GPLv3 text is available below.");
        WearMenus.consent(this,step,title,warning,()->{WatchConsent.accept(this,step);if(WatchConsent.ready(this)){showFace();WatchTransport.request(this);}else consent();},
                step==2?()->WearMenus.document(this,"GPLv3",asset("gpl-3.0.txt"),this::consent):null);
    }
    private void showFace(){
        if(!WatchConsent.ready(this)){consent();return;}
        WatchRenderer renderer=new WatchRenderer(this);
        face=new View(this){@Override protected void onDraw(Canvas c){
            long now=System.currentTimeMillis();WatchStore.Entry e=WatchStore.read(WatchActivity.this);
            WatchSnapshot s=demo?WatchRenderer.demo(now):e==null?null:e.snapshot;
            int size=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-size)/2f,(getHeight()-size)/2f);
            String age=demo||e!=null&&e.clockValid(WatchActivity.this,now)?WatchRenderer.age(s,now):"—";
            renderer.draw(c,size,s,demo||e!=null&&e.fresh(WatchActivity.this,now),demo&&ambientPreview,WatchStore.hours(WatchActivity.this),true,demo,now,age);c.restore();
            setContentDescription(s==null?WatchConsent.text("Kein Wert. Antippen für Optionen.","No reading. Tap for options."):
                    WatchRenderer.number(s.value,s.mmol,false)+" "+(s.mmol?"mmol/l":"mg/dl")+". "+
                    WatchConsent.text("Alter in Minuten: ","Age in minutes: ")+age+". "+
                    WatchConsent.text("Verlauf in Stunden: ","History in hours: ")+WatchStore.hours(WatchActivity.this)+". "+(demo?"DEMO":""));
        }};
        face.setOnClickListener(v->menu());setContentView(face);
    }
    private void menu(){
        face=null;
        String[] labels={WatchConsent.text("Aktualisieren","Refresh"),WatchConsent.text("Verlauf · ","History · ")+WatchStore.hours(this)+" h",WatchConsent.text("Alarme","Alerts"),
                demo?WatchConsent.text("Live-Daten","Live readings"):WatchConsent.text("Vorschau · DEMO","Preview · DEMO"),
                WatchConsent.text("Gedimmt · DEMO","Ambient · DEMO"),WatchConsent.text("Lizenzen","Licenses")};
        Runnable[] actions={()->{WatchTransport.request(this);demo=false;showFace();},
                ()->{WatchStore.hours(this,WatchStore.hours(this)==1?2:1);DisplayComplication.update(this);menu();},this::alertSettings,
                ()->{demo=!demo;ambientPreview=false;showFace();},()->{demo=true;ambientPreview=true;showFace();},this::licenses};
        WearMenus.menu(this,"GVA Watch",labels,actions,this::showFace);
    }
    private void alertSettings(){
        String state=WatchAlerts.enabled(this)&&WatchAlerts.permission(this)?WatchConsent.text("Alarme · Ein","Alerts · On"):WatchConsent.text("Alarme · Aus","Alerts · Off");
        WearMenus.menu(this,WatchConsent.text("Telefonalarme","Phone alerts"),new String[]{state,WatchConsent.text("Android-Freigabe","Android permission"),WatchConsent.text("Hinweise","Details")},
                new Runnable[]{()->{if(WatchAlerts.enabled(this))WatchAlerts.set(this,false);else if(android.os.Build.VERSION.SDK_INT>=33&&!WatchAlerts.permission(this))requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},44);else WatchAlerts.set(this,true);alertSettings();},
                    ()->startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())),
                    ()->WearMenus.document(this,WatchConsent.text("Telefonalarme","Phone alerts"),WatchConsent.text("Empfängt GVA-Alarme und Datenlückenwarnungen mit zwei kurzen Vibrationsimpulsen. Auch am Telefon unter Darstellung → Uhr einschalten. Verbindung und Android-Freigabe sind erforderlich. Stummmodus oder Nicht stören können die Vibration verhindern. Wegwischen auf der Uhr quittiert den Telefonalarm nicht.",
                    "Receives GVA alarms and data-gap warnings with two short vibration pulses. Also enable sending under Appearance → Watch on the phone. A connection and Android permission are required. Silent or Do Not Disturb settings may prevent vibration. Dismissing the watch card does not acknowledge the phone alarm."),this::alertSettings)},this::menu);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==44){WatchAlerts.set(this,results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED);alertSettings();}}
    private void licenses(){
        StringBuilder content=new StringBuilder("GVA Watch · GPLv3\nRoboto Flex · SIL OFL 1.1\nAndroidX · Apache 2.0\nGoogle Play services Wearable · Google SDK terms\n\n");
        for(String name:new String[]{"gpl-3.0.txt","RobotoFlex-OFL.txt","Apache-2.0.txt"})content.append(asset(name)).append("\n\n");
        WearMenus.document(this,WatchConsent.text("Lizenzen","Licenses"),content.toString(),this::menu);
    }
    private String asset(String name){try(java.io.InputStream in=getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){return WatchConsent.text("Text nicht verfügbar","Text unavailable");}}
    @Override protected void onResume(){super.onResume();handler.post(refresh);if(WatchConsent.ready(this))WatchTransport.request(this);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
