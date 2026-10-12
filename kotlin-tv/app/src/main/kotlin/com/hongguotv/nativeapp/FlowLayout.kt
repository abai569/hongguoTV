// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Left-to-right wrapping container; children keep their measured width and never get truncated. */
class FlowLayout(context: Context, private val horizontalGap: Int, private val verticalGap: Int): ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode=MeasureSpec.getMode(widthMeasureSpec)
        val widthSize=MeasureSpec.getSize(widthMeasureSpec)
        val available=if(widthMode==MeasureSpec.UNSPECIFIED) 0 else widthSize
        val heightSize=MeasureSpec.getSize(heightMeasureSpec)
        val childWidthSpec=if(widthMode==MeasureSpec.UNSPECIFIED) MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED) else MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST)
        val childHeightSpec=MeasureSpec.makeMeasureSpec(heightSize,MeasureSpec.AT_MOST)
        var lineWidth=0; var lineHeight=0; var totalHeight=0; var maxWidth=0
        for(index in 0 until childCount) {
            val child=getChildAt(index)
            if(child.visibility==View.GONE) continue
            child.measure(childWidthSpec,childHeightSpec)
            val childWidth=child.measuredWidth; val childHeight=child.measuredHeight
            if(lineWidth>0 && lineWidth+horizontalGap+childWidth>available) {
                totalHeight+=lineHeight+verticalGap
                maxWidth=maxOf(maxWidth,lineWidth)
                lineWidth=childWidth; lineHeight=childHeight
            } else {
                lineWidth=if(lineWidth==0) childWidth else lineWidth+horizontalGap+childWidth
                lineHeight=maxOf(lineHeight,childHeight)
            }
        }
        totalHeight+=lineHeight
        maxWidth=maxOf(maxWidth,lineWidth)
        val width=if(widthMode==MeasureSpec.EXACTLY) widthSize else maxWidth+paddingLeft+paddingRight
        setMeasuredDimension(width,totalHeight+paddingTop+paddingBottom)
    }
    override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) {
        val available=(right-left)-paddingLeft-paddingRight
        var x=paddingLeft; var y=paddingTop; var lineHeight=0
        for(index in 0 until childCount) {
            val child=getChildAt(index)
            if(child.visibility==View.GONE) continue
            val childWidth=child.measuredWidth; val childHeight=child.measuredHeight
            if(x>paddingLeft && x+childWidth>paddingLeft+available) {
                x=paddingLeft; y+=lineHeight+verticalGap; lineHeight=0
            }
            child.layout(x,y,x+childWidth,y+childHeight)
            x+=childWidth+horizontalGap
            lineHeight=maxOf(lineHeight,childHeight)
        }
    }
}
