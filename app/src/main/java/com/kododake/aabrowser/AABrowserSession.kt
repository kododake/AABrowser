package com.kododake.aabrowser

import android.content.Intent
import androidx.car.app.Session

class AABrowserSession : Session() {
    override fun onCreateScreen(intent: Intent) = AABrowserScreen(carContext)
}