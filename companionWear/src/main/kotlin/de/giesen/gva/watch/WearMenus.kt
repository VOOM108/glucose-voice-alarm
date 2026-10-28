// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*

/** Official Wear Material 3 menus; Canvas/WFF continue to own the data display. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object WearMenus {
    private val robotoFlex = FontFamily(
        Font(R.font.roboto_flex, FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.roboto_flex, FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(750)))
    )
    @JvmStatic fun menu(activity: Activity, title: String, labels: Array<String>, actions: Array<Runnable>, back: Runnable) {
        require(labels.size == actions.size)
        install(activity) {
            Page(title, WatchConsent.text("Anzeige", "Display"), true, back) {
                for (i in labels.indices) item {
                    Button(onClick = { actions[i].run() }, colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer), modifier = Modifier.fillMaxWidth().testTag("watch-menu-$i")) {
                        Text(labels[i], maxLines = 2)
                    }
                }
            }
        }
    }
    @JvmStatic fun consent(activity: Activity, step: Int, title: String, warning: String, confirm: Runnable, license: Runnable?) {
        install(activity) {
            var agreed by remember(step) { mutableStateOf(false) }
            Page(title, WatchConsent.text("Bestätigen", "Confirm"), agreed, Runnable { if(agreed) confirm.run() }) {
                item { Text(warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
                item {
                    CheckboxButton(checked = agreed, onCheckedChange = { agreed = it },
                        modifier = Modifier.fillMaxWidth().testTag("watch-consent-checkbox"),
                        label = { Text(WatchConsent.text("Ich verstehe", "I understand")) })
                }
                if(license != null) item {
                    ChildButton(onClick = { license.run() }, modifier = Modifier.fillMaxWidth()) { Text("GPLv3") }
                }
            }
        }
    }
    @JvmStatic fun document(activity: Activity, title: String, content: String, back: Runnable) {
        val paragraphs = content.split("\n\n").flatMap { it.chunked(1800) }.take(400)
        install(activity) {
            Page(title, WatchConsent.text("Zurück", "Back"), true, back) {
                for(paragraph in paragraphs) item { Text(paragraph, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    private fun install(activity: Activity, content: @Composable () -> Unit) {
        val view = ComposeView(activity)
        view.setContent {
            MaterialTheme(colorScheme = ColorScheme(primary=Color(0xFFC4E4D7),onPrimary=Color(0xFF10231B),
                primaryContainer=Color(0xFF263C32),onPrimaryContainer=Color(0xFFE0F3E9)),
                typography = Typography(defaultFontFamily = robotoFlex)) { AppScaffold { content() } }
        }
        activity.setContentView(view)
    }
    @Composable private fun Page(title: String, edge: String, enabled: Boolean, back: Runnable,
            items: androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.() -> Unit) {
        val state = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = state,
            contentPadding = PaddingValues(start=24.dp,end=24.dp,top=42.dp,bottom=12.dp),
            edgeButton = { EdgeButton(onClick = { back.run() }, enabled = enabled,
                modifier = Modifier.testTag("watch-edge-action")) { Text(edge) } }) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding,
                modifier = Modifier.fillMaxSize().testTag("watch-menu-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text(title, style=MaterialTheme.typography.titleMedium, modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) }
                items()
            }
        }
    }
}
