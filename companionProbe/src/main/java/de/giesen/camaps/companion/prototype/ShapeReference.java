// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.security.*;
import java.util.*;

/** Color-free identity: crop/scale uniformly, but never rotate, mirror or infer a direction. */
public final class ShapeReference {
    public static final String CONFLICT="CONFLICT";
    public static String fingerprint(String profile,int[] argb,int width,int height) {
        if (profile==null || profile.isEmpty() || width<2 || height<2 || width>256 || height>256
                || argb==null || argb.length!=width*height) throw new IllegalArgumentException("Invalid shape input");
        int maximum=0; for (int pixel:argb) maximum=Math.max(maximum,pixel>>>24);
        if (maximum<16) return null;
        int cutoff=Math.max(8,(maximum+1)/2),left=width,top=height,right=-1,bottom=-1,count=0;
        boolean[] mask=new boolean[argb.length];
        Integer foreground=null;
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) if ((argb[y*width+x]>>>24)>=cutoff) {
            int rgb=argb[y*width+x]&0x00ffffff;
            // A filled status circle can hide differently directed white arrows in the same alpha mask.
            // Only a single-colour foreground can safely use an alpha-only identity.
            if(foreground!=null && foreground!=rgb)return null;
            foreground=rgb;
            mask[y*width+x]=true; left=Math.min(left,x); top=Math.min(top,y); right=Math.max(right,x); bottom=Math.max(bottom,y); count++;
        }
        int w=right-left+1,h=bottom-top+1;
        // Opaque rectangles lose internal color distinctions in an alpha-only mask. Reject them.
        if (count<8 || w<2 || h<2 || count*100L>=(long)w*h*90) return null;
        int rw=w>=h?60:Math.max(1,60*w/h),rh=h>=w?60:Math.max(1,60*h/w),retained=0;
        byte[] normalized=new byte[64*64];
        for (int y=0;y<rh;y++) for (int x=0;x<rw;x++) {
            int sourceX=left+Math.min(w-1,(int)((x+0.5)*w/rw));
            int sourceY=top+Math.min(h-1,(int)((y+0.5)*h/rh));
            if (mask[sourceY*width+sourceX]) { normalized[((64-rh)/2+y)*64+(64-rw)/2+x]=1; retained++; }
        }
        if (retained<4) return null;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            digest.update(("shape-v1:"+profile).getBytes(java.nio.charset.StandardCharsets.UTF_8)); digest.update((byte)0); digest.update(normalized);
            StringBuilder key=new StringBuilder(); for (byte value:digest.digest()) key.append(String.format(Locale.ROOT,"%02x",value&255)); return key.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String registration(Object previous,NotificationParser.Trend label) {
        if (label==null || label==NotificationParser.Trend.UNKNOWN) throw new IllegalArgumentException("Unknown shape label");
        return previous==null || label.name().equals(previous)?label.name():CONFLICT;
    }
    public static NotificationParser.Trend resolve(NotificationParser.Trend exact,NotificationParser.Trend shape) {
        if (exact!=NotificationParser.Trend.UNKNOWN && shape!=NotificationParser.Trend.UNKNOWN && exact!=shape)
            return NotificationParser.Trend.UNKNOWN;
        return exact!=NotificationParser.Trend.UNKNOWN?exact:shape;
    }
    private ShapeReference() {}
}
