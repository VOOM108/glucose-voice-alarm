// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.giesen.camaps.companion.prototype.NotificationParser.Trend.*;

/** Hand-drawn synthetic masks, never taken from CamAPS or user data. */
public class ShapeReferenceTest {
    private void line(int[] image,int x,int y,int endX,int endY) {
        int steps=Math.max(Math.abs(endX-x),Math.abs(endY-y));
        for (int i=0;i<=steps;i++) {
            int px=x+(endX-x)*i/steps,py=y+(endY-y)*i/steps;
            image[py*24+px]=0xff000000; if (px+1<24) image[py*24+px+1]=0xff000000;
        }
    }
    private int[] arrow(NotificationParser.Trend trend) {
        int[] image=new int[24*24];
        if (trend==FLAT) { line(image,3,12,20,12); line(image,20,12,13,5); line(image,20,12,13,19); }
        else if (trend==UP_RIGHT || trend==DOWN_RIGHT) { line(image,4,20,19,5); line(image,19,5,8,5); line(image,19,5,19,16); }
        else {
            line(image,11,20,11,3); line(image,11,3,4,10); line(image,11,3,18,10);
            if (trend==DOUBLE_UP || trend==DOUBLE_DOWN) { line(image,11,10,4,17); line(image,11,10,18,17); }
        }
        if (trend==DOWN || trend==DOUBLE_DOWN || trend==DOWN_RIGHT) {
            int[] mirrored=new int[24*24]; for (int y=0;y<24;y++) for (int x=0;x<24;x++) mirrored[(23-y)*24+x]=image[y*24+x]; return mirrored;
        }
        return image;
    }
    private String key(int[] image,int size) { return ShapeReference.fingerprint("package:version",image,size,size); }
    @Test public void allSevenSyntheticShapesRemainDistinct() {
        Set<String> keys=new HashSet<>();
        for (NotificationParser.Trend trend:new NotificationParser.Trend[]{DOUBLE_UP,UP,UP_RIGHT,FLAT,DOWN_RIGHT,DOWN,DOUBLE_DOWN}) {
            String key=key(arrow(trend),24); assertNotNull(trend.name(),key); assertTrue(trend.name(),keys.add(key));
        }
        assertEquals(7,keys.size());
    }
    @Test public void colorAndUniformOpacityDoNotChangeShapeIdentity() {
        int[] original=arrow(UP_RIGHT),variant=original.clone();
        for (int i=0;i<variant.length;i++) if (variant[i]!=0) variant[i]=0x8040ff20;
        assertEquals(key(original,24),key(variant,24));
    }
    @Test public void integerScaleAndPaddingDoNotRequireANewLabel() {
        int[] original=arrow(UP_RIGHT),padded=new int[64*64],scaled=new int[48*48];
        for (int y=0;y<24;y++) for (int x=0;x<24;x++) {
            padded[(y+9)*64+x+7]=original[y*24+x];
            for (int yy=0;yy<2;yy++) for (int xx=0;xx<2;xx++) scaled[(y*2+yy)*48+x*2+xx]=original[y*24+x];
        }
        assertEquals(key(original,24),key(padded,64)); assertEquals(key(original,24),key(scaled,48));
    }
    @Test public void noRotationOrMirroringIsNormalizedAway() {
        assertNotEquals(key(arrow(UP_RIGHT),24),key(arrow(DOWN_RIGHT),24));
        assertNotEquals(key(arrow(UP),24),key(arrow(DOWN),24));
        assertNotEquals(key(arrow(UP),24),key(arrow(DOUBLE_UP),24));
    }
    @Test public void opaqueColorImagesCannotBecomeFalseCommonShape() {
        int[] rectangle=new int[24*24]; Arrays.fill(rectangle,0xff336699);
        assertNull(key(rectangle,24)); assertNull(key(new int[24*24],24));
    }
    @Test public void sourceVersionStillBoundsTheConfirmation() {
        assertNotEquals(key(arrow(UP),24),ShapeReference.fingerprint("another-version",arrow(UP),24,24));
    }
    @Test public void contradictoryLabelsDoNotSilentlyOverwriteAGeometryReference() {
        assertEquals(UP_RIGHT.name(),ShapeReference.registration(null,UP_RIGHT));
        assertEquals(UP_RIGHT.name(),ShapeReference.registration(UP_RIGHT.name(),UP_RIGHT));
        assertEquals(ShapeReference.CONFLICT,ShapeReference.registration(UP_RIGHT.name(),FLAT));
        assertEquals(ShapeReference.CONFLICT,ShapeReference.registration(ShapeReference.CONFLICT,UP_RIGHT));
        assertEquals(UNKNOWN,ShapeReference.resolve(FLAT,UP_RIGHT));
        assertEquals(UP_RIGHT,ShapeReference.resolve(UNKNOWN,UP_RIGHT)); assertEquals(FLAT,ShapeReference.resolve(FLAT,UNKNOWN));
    }
}
