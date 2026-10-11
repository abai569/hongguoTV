// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView

/** Brings the focused child into view with a short animation instead of the platform's instant jump. */
class SmoothScrollView(context: Context): ScrollView(context) {
    init { isVerticalScrollBarEnabled=false; clipToPadding=false; isSmoothScrollingEnabled=true }
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        if(immediate) return super.requestChildRectangleOnScreen(child,rectangle,true)
        val rect=Rect(rectangle)
        offsetDescendantRectToMyCoords(child,rect)
        val bottomLimit=height-paddingBottom
        val delta=when {
            rect.bottom>bottomLimit -> rect.bottom-bottomLimit
            rect.top<paddingTop -> rect.top-paddingTop
            else -> 0
        }
        if(delta!=0) { smoothScrollBy(0,delta); return true }
        return false
    }
}
