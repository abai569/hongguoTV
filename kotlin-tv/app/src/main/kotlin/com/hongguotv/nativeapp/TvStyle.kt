// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Shared native colors and shapes; no blur, bitmap backgrounds or focus animation. */
object TvStyle {
    val background=Color.rgb(11,15,21)
    val surface=Color.rgb(23,30,40)
    val raised=Color.rgb(32,42,54)
    val accent=Color.rgb(255,137,100)
    val text=Color.rgb(243,244,246)
    val muted=Color.rgb(147,160,178)
    val outline=Color.rgb(43,55,70)
    const val POSTER_ASPECT=.7f
    fun shape(context: Context,color: Int,border: Int=Color.TRANSPARENT,radius: Int=10)=GradientDrawable().apply {
        setColor(color); cornerRadius=radius*context.resources.displayMetrics.density
        setStroke((2*context.resources.displayMetrics.density).toInt(),border)
    }
    fun card(context: Context,focused: Boolean)=shape(context,if(focused) raised else surface,if(focused) accent else Color.TRANSPARENT)
    /** Ease the focus highlight with a short scale-in so the selection no longer snaps. */
    fun animateFocus(view: View,focused: Boolean) {
        view.animate().cancel()
        val scale=if(focused) 1.05f else 1f
        view.animate().scaleX(scale).scaleY(scale).setDuration(150).setInterpolator(DecelerateInterpolator()).start()
    }
}
