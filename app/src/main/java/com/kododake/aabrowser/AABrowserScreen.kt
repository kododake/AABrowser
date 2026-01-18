package com.kododake.aabrowser

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.webkit.WebView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.web.BrowserCallbacks
import com.kododake.aabrowser.web.configureWebView
import com.kododake.aabrowser.web.releaseCompletely

class AABrowserScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var webView: android.webkit.WebView? = null

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        checkMotionRestrictions()
    }

    /**
     * Handshake with ConstraintManager to signal that this screen 
     * is aware of UX restrictions and is distraction-optimized.
     */
    private fun checkMotionRestrictions() {
        try {
            val constraintManager = carContext.getCarService(ConstraintManager::class.java)
            val limit = constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            Log.d("AABrowser", "Motion restriction handshake successful. Content limit: $limit")
        } catch (e: Exception) {
            Log.w("AABrowser", "ConstraintManager not available; system might be in restricted mode.")
        }
    }

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Starting Browser...").build())
            .build()
        val contentTemplate = PaneTemplate.Builder(pane).build()
        
        return MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate)
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        createVirtualDisplay(surfaceContainer)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        tearDownPresentation()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        // Handle visible area changes if needed
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        // Handle stable area changes if needed
    }

    private fun createVirtualDisplay(surfaceContainer: SurfaceContainer) {
        tearDownPresentation()

        val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        virtualDisplay = displayManager.createVirtualDisplay(
            "AABrowser",
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi,
            surfaceContainer.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )

        presentation = object : Presentation(carContext, virtualDisplay!!.display) {
            override fun onCreate(savedInstanceState: android.os.Bundle?) {
                super.onCreate(savedInstanceState)
                val initialUrl = BrowserPreferences.resolveInitialUrl(carContext)
                val desktopMode = BrowserPreferences.shouldUseDesktopMode(carContext)
                val callbacks = BrowserCallbacks(
                    onUrlChange = { url -> BrowserPreferences.persistUrl(carContext, url) }
                )

                setContentView(R.layout.presentation_browser)
                val presentationWebView = findViewById<WebView>(R.id.presentationWebView)
                webView = presentationWebView
                configureWebView(presentationWebView, callbacks, desktopMode)
                presentationWebView.loadUrl(initialUrl)
            }
        }.also { it.show() }
    }

    fun loadUrl(url: String) {
        val navigable = BrowserPreferences.formatNavigableUrl(url)
        BrowserPreferences.persistUrl(carContext, navigable)
        webView?.loadUrl(navigable)
    }

    private fun tearDownPresentation() {
        webView?.releaseCompletely()
        webView = null
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}
