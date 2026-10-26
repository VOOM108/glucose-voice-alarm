// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import android.content.Context;
/** Explicit synthetic setup for unrelated platform tests. Never compiled into the APK. */
final class ConsentTestSupport {
    static void accept(Context c) {
        if(SafetyConsent.stage(c)==SafetyConsent.Stage.WARNING && !SafetyConsent.acknowledge(c,SafetyConsent.Stage.WARNING,true))throw new AssertionError("Warning setup");
        if(SafetyConsent.stage(c)==SafetyConsent.Stage.TERMS && !SafetyConsent.acknowledge(c,SafetyConsent.Stage.TERMS,true))throw new AssertionError("Terms setup");
        if(!SafetyConsent.ready(c))throw new AssertionError("Consent setup incomplete");
    }
}
