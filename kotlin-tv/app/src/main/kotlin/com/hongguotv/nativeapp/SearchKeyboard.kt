// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** TV on-screen keyboard driven by the remote: letters, digits and edit keys. */
class SearchKeyboard(
    context: Context,
    private val onChange: (String)->Unit,
    private val onSearch: ()->Unit,
    private val onPush: ()->Unit
): LinearLayout(context) {
    private val rows=mutableListOf<MutableList<TextView>>()
    private var text=""
    val firstKeyId: Int get()=rows.firstOrNull()?.firstOrNull()?.id ?: View.NO_ID
    val clearKeyId: Int get()=rows.getOrNull(0)?.getOrNull(1)?.id ?: View.NO_ID

    private fun dpi(value: Int)=(value*resources.displayMetrics.density).toInt()
    private val cols=6
    private val gapX=dpi(6)
    private val gapY=dpi(3)
    private var keySize=dpi(42)
    private fun keyTextFor(size: Int)=((size/resources.displayMetrics.density)*0.45f).coerceIn(9f,17f)

    private fun keyRow(): LinearLayout {
        val row=LinearLayout(context).apply { orientation=HORIZONTAL; gravity=Gravity.START }
        addView(row,LayoutParams(-1,-2))
        rows.add(mutableListOf())
        return row
    }
    private fun addKey(row: LinearLayout,label: String,weight: Float=1f,action: ()->Unit) {
        val view=TextView(context).apply {
            id=View.generateViewId(); text=label; gravity=Gravity.CENTER
            setTextColor(TvStyle.text); textSize=keyTextFor(keySize); includeFontPadding=false
            background=TvStyle.shape(context,TvStyle.surface,Color.TRANSPARENT,8)
            isFocusable=true; isFocusableInTouchMode=context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            setOnFocusChangeListener { _,focused -> background=TvStyle.shape(context,if(focused) TvStyle.raised else TvStyle.surface,if(focused) TvStyle.accent else Color.TRANSPARENT,8) }
            setOnClickListener { action() }
        }
        row.addView(view,LayoutParams(keySize,keySize).apply { marginEnd=gapX; topMargin=gapY })
        rows.last().add(view)
    }
    private fun char(value: Char) {
        if(text.length>=80) return
        text+=value; onChange(text)
    }
    fun setText(value: String) { text=value.take(80); onChange(text) }
    fun value(): String = text
    fun focusFirst() { post { rows.firstOrNull()?.firstOrNull()?.requestFocus() } }
    fun linkRight(targetId: Int) { if(targetId==View.NO_ID) return; rows.forEach { it.lastOrNull()?.nextFocusRightId=targetId } }
    fun linkLeft(targetId: Int) { if(targetId==View.NO_ID) return; rows.forEach { it.firstOrNull()?.nextFocusLeftId=targetId } }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val wMode=MeasureSpec.getMode(widthMeasureSpec)
        val wSize=MeasureSpec.getSize(widthMeasureSpec)
        val hMode=MeasureSpec.getMode(heightMeasureSpec)
        val hSize=MeasureSpec.getSize(heightMeasureSpec)
        val dm=resources.displayMetrics
        val width=if(wMode==MeasureSpec.EXACTLY && wSize>0) wSize else (dm.widthPixels*0.38f).toInt()
        val availH=if(hMode!=MeasureSpec.UNSPECIFIED && hSize>0) hSize else (dm.heightPixels*0.55f).toInt()
        val rowN=childCount.coerceAtLeast(1)
        val byW=(width-gapX*(cols-1))/cols
        val byH=(availH-gapY*rowN)/rowN
        val floor=dpi(20)
        var size=minOf(byW,byH)
        if(size<floor) size=floor
        if(byW>=floor && size>byW) size=byW
        if(size!=keySize) {
            keySize=size; val ts=keyTextFor(size)
            rows.forEach { r -> r.forEach { k -> k.layoutParams=LayoutParams(size,size).apply { marginEnd=gapX; topMargin=gapY }; k.textSize=ts } }
        }
        val measuredW=if(wMode==MeasureSpec.EXACTLY) width else cols*keySize+gapX*(cols-1)
        setMeasuredDimension(measuredW,rowN*(keySize+gapY))
    }

    init {
        orientation=VERTICAL
        val functions=keyRow()
        addKey(functions,"搜索",1.6f) { onSearch() }
        addKey(functions,"清空",1.2f) { text=""; onChange(text) }
        addKey(functions,"删除",1.2f) { if(text.isNotEmpty()) { text=text.dropLast(1); onChange(text) } }
        addKey(functions,"推送",1.2f) { onPush() }
        listOf("ABCDEF","GHIJKL","MNOPQR","STUVWX","YZ0123","456789").forEach { letters ->
            val row=keyRow()
            letters.forEach { letter -> addKey(row,letter.toString()) { char(letter) } }
        }
        rows.forEachIndexed { rowIndex,row ->
            row.forEachIndexed { column,view ->
                if(column>0) view.nextFocusLeftId=row[column-1].id
                if(column<row.lastIndex) view.nextFocusRightId=row[column+1].id
                rows.getOrNull(rowIndex-1)?.getOrNull(column)?.let { view.nextFocusUpId=it.id }
                rows.getOrNull(rowIndex+1)?.getOrNull(column)?.let { view.nextFocusDownId=it.id }
            }
        }
    }
}
