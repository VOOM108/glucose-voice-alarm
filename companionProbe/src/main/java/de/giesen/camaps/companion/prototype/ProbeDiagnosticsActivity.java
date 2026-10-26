// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
/** Internal diagnostic destination; never the launcher or the current-value dashboard. */
public final class ProbeDiagnosticsActivity extends ProbeActivity {
    @Override protected boolean diagnosticsPage() { return true; }
}
