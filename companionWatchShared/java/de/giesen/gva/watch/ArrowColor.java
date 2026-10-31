// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
/** Explicit display annotation only; never an alarm direction or a derived glucose status. */
public enum ArrowColor {
    NORMAL(0,0,0), RED(0xffe53935,0xffff5c5c,0xffe65353), YELLOW(0xffb88700,0xffffd54f,0xffe0bd50);
    public final int phone,watch,ambient;
    ArrowColor(int phone,int watch,int ambient){this.phone=phone;this.watch=watch;this.ambient=ambient;}
    public boolean colored(){return this!=NORMAL;}
}
