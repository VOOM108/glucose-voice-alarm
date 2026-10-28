// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import java.text.SimpleDateFormat;
import java.util.*;
/** Last local send attempt only; never a receipt or vibration acknowledgement. */
final class WatchTransferStatus {
    enum Event { DISPLAY,DISPLAY_CLEAR,ALARM,TEST,UPDATE,ALARM_CLEAR }
    enum Result { SENT,NO_NEARBY,FAILED,NO_DATA }
    static final WatchTransferStatus EMPTY=new WatchTransferStatus(null,null,0);
    final Event event;final Result result;final long completedWall;
    WatchTransferStatus(Event event,Result result,long wall){this.event=event;this.result=result;completedWall=wall;}
    static WatchTransferStatus completed(Event event,Result result){return new WatchTransferStatus(event,result,System.currentTimeMillis());}
    String line(String label){
        if(completedWall==0)return label+": "+WatchPublisher.tr("Noch kein Versand","No send attempt yet");
        String message;
        if(result==Result.NO_NEARBY)message=WatchPublisher.tr("Keine nahe Uhr","No nearby watch");
        else if(result==Result.FAILED)message=WatchPublisher.tr("Nicht gesendet","Not sent");
        else if(result==Result.NO_DATA)message=WatchPublisher.tr("Kein Anzeigestand","No display data");
        else switch(event){
            case TEST:message=WatchPublisher.tr("Test gesendet","Test sent");break;
            case ALARM:message=WatchPublisher.tr("Alarm gesendet","Alert sent");break;
            case UPDATE:message=WatchPublisher.tr("Aktualisierung gesendet","Update sent");break;
            case DISPLAY_CLEAR:case ALARM_CLEAR:message=WatchPublisher.tr("Löschmeldung gesendet","Clear sent");break;
            default:message=WatchPublisher.tr("Gesendet","Sent");
        }
        String pattern=android.text.format.DateUtils.isToday(completedWall)?"HH:mm":"dd.MM. HH:mm";
        return label+": "+message+" · "+new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(completedWall));
    }
    static Event alert(de.giesen.gva.watch.WatchAlert.Action action,boolean test){
        return action==de.giesen.gva.watch.WatchAlert.Action.CLEAR?Event.ALARM_CLEAR:
            action==de.giesen.gva.watch.WatchAlert.Action.UPDATE?Event.UPDATE:test?Event.TEST:Event.ALARM;
    }
}
