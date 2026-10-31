// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import java.util.*;
import de.giesen.gva.watch.ArrowColor;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.NotificationParser.Trend.*;
/** Synthetic reference keys only. No device references or glucose data. */
public class RedArrowReferenceTest {
    private final String exact=String.join("",Collections.nCopies(64,"a")),other=String.join("",Collections.nCopies(64,"b"));
    @Test public void everyRedAndYellowAssignmentRetainsItsOriginalDirection() {
        for(ArrowColor color:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW})for(NotificationParser.Trend direction:TrendDisplayWords.TRENDS) {
            Map<String,Object> refs=Collections.singletonMap(exact,TrendReference.label(direction,color));
            assertEquals(direction,TrendReference.lookup(refs,exact));assertEquals(color,TrendReference.color(refs,exact,direction));
            assertEquals(ArrowColor.NORMAL,TrendReference.color(refs,other,direction));assertEquals(ArrowColor.NORMAL,TrendReference.color(refs,exact,UNKNOWN));
        }
    }
    @Test public void oldAndNormalAssignmentsNeverAcquireColourFromDirectionAlone() {
        for(NotificationParser.Trend direction:TrendDisplayWords.TRENDS) {
            Map<String,Object> refs=Collections.singletonMap(exact,direction.name());
            assertEquals(direction,TrendReference.lookup(refs,exact));assertEquals(ArrowColor.NORMAL,TrendReference.color(refs,exact,direction));
        }
    }
    @Test public void reassignmentChangesOnlyTheExplicitColourOfTheExactReference() {
        Map<String,Object> refs=new HashMap<>();refs.put(other,UP.name());
        for(ArrowColor color:ArrowColor.values()) {
            refs.put(exact,TrendReference.label(FLAT,color));assertEquals(color,TrendReference.color(refs,exact,FLAT));assertEquals(UP,TrendReference.lookup(refs,other));
        }
    }
    @Test public void directionConflictDropsColourInsteadOfColouringAnUnconfirmedInterpretation() {
        for(ArrowColor color:new ArrowColor[]{ArrowColor.RED,ArrowColor.YELLOW}) {
            Map<String,Object> refs=Collections.singletonMap(exact,TrendReference.label(UP,color));assertEquals(ArrowColor.NORMAL,TrendReference.color(refs,exact,DOWN));
            NotificationParser.Result r=new NotificationParser.Result(123,UP,"synthetic").withArrowColor(color);
            assertEquals(color,r.arrowColor);assertEquals(ArrowColor.NORMAL,r.withTrend(UNKNOWN,"conflict").arrowColor);assertEquals(ArrowColor.NORMAL,r.withTrend(DOWN,"changed").arrowColor);
        }
    }
    @Test public void unknownOrMalformedReferencesCannotCreateAColouredDirection() {
        for(Object value:Arrays.asList("RED_UNKNOWN","RED_RED_UP","YELLOW_RED_UP","YELLOW_nonsense",17)) {
            Map<String,Object> refs=Collections.singletonMap(exact,value);assertEquals(UNKNOWN,TrendReference.lookup(refs,exact));
            assertEquals(ArrowColor.NORMAL,TrendReference.color(refs,exact,UNKNOWN));
        }
    }
    @Test public void shapeConfirmationAlwaysKeepsOnlyTheDirection() {
        for(NotificationParser.Trend direction:TrendDisplayWords.TRENDS) {
            Map<String,Object> shapes=Collections.singletonMap(other,ShapeReference.registration(null,direction));
            assertEquals(direction,TrendReference.lookup(shapes,other));assertEquals(ArrowColor.NORMAL,TrendReference.color(shapes,other,direction));
        }
    }
}
