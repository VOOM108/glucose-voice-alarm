// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="en-w225dp-h225dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearMenuSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<WatchActivity>()
    private fun capture(name:String) {
        val folder=File(requireNotNull(System.getProperty("gva.watch.renders")));folder.mkdirs()
        compose.runOnUiThread {
            val view=compose.activity.window.decorView
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(225,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(225,android.view.View.MeasureSpec.EXACTLY))
            view.layout(0,0,225,225);view.invalidate()
        }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view=compose.activity.window.decorView
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        var visible=0
        for(y in 45 until 205)for(x in 20 until 205)if(bitmap.getPixel(x,y) and 0x00ffffff != 0)visible++
        assertTrue("Menu preview must contain rendered content below the system clock",visible>300)
        FileOutputStream(File(folder,name+".png")).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun eachConsentStepRequiresItsOwnUncheckedConfirmation() {
        compose.onNodeWithTag("watch-edge-action").assertIsNotEnabled()
        capture("material3-consent")
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasTestTag("watch-consent-checkbox"))
        compose.onNodeWithTag("watch-consent-checkbox").assertIsOff().performClick()
        compose.onNodeWithTag("watch-menu-list").performScrollToIndex(2)
        compose.onNodeWithTag("watch-menu-list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("watch-edge-action").assertIsDisplayed()
        compose.onNodeWithTag("watch-edge-action").assertIsEnabled().performClick()
        compose.onNodeWithTag("watch-edge-action").assertIsNotEnabled()
        assertFalse(WatchConsent.ready(compose.activity))
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasTestTag("watch-consent-checkbox"))
        compose.onNodeWithTag("watch-consent-checkbox").assertIsOff().performClick()
        compose.onNodeWithTag("watch-menu-list").performScrollToIndex(3)
        compose.onNodeWithTag("watch-menu-list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("watch-edge-action").assertIsDisplayed()
        compose.onNodeWithTag("watch-edge-action").performClick()
        assertTrue(WatchConsent.ready(compose.activity))
    }
    @Test fun menuActionsAndBottomReturnKeepDistinctCallbacks() {
        val clicked=AtomicInteger();val returned=AtomicInteger()
        compose.runOnUiThread {
            WearMenus.menu(compose.activity,"GVA Watch",arrayOf("Refresh","History · 1 h"),
                arrayOf(Runnable{clicked.incrementAndGet()},Runnable{clicked.addAndGet(10)}),Runnable{returned.incrementAndGet()})
        }
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasText("Refresh"))
        compose.onNodeWithText("Refresh").performClick()
        assertEquals(1,clicked.get())
        capture("material3-menu")
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasText("History · 1 h"))
        compose.onNodeWithText("History · 1 h").performClick()
        assertEquals(11,clicked.get())
        compose.onNodeWithTag("watch-edge-action").performClick()
        assertEquals(1,returned.get())
    }
    @Test fun watchAlertMenuRequiresAnExplicitToggleAndKeepsItSeparateFromDisplay() {
        compose.runOnUiThread {
            WatchConsent.accept(compose.activity,1); WatchConsent.accept(compose.activity,2)
            org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(compose.activity,"menu")
        }
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasText("Alerts"))
        compose.onNodeWithText("Alerts").performClick()
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasText("Alerts · Off"))
        assertFalse(WatchAlerts.enabled(compose.activity))
        capture("material3-alerts")
        compose.onNodeWithText("Alerts · Off").performClick()
        compose.waitForIdle(); assertTrue(WatchAlerts.enabled(compose.activity))
        compose.onNodeWithTag("watch-menu-list").performScrollToNode(hasText("Alerts · On"))
        compose.onNodeWithText("Alerts · On").performClick()
        compose.waitForIdle(); assertFalse(WatchAlerts.enabled(compose.activity))
    }
}
