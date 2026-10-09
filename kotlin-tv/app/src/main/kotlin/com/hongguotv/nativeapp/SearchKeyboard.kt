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

    private fun keyRow(): LinearLayout {
        val row=LinearLayout(context).apply { orientation=HORIZONTAL }
        addView(row,LayoutParams(-1,-2))
        rows.add(mutableListOf())
        return row
    }
    private fun addKey(row: LinearLayout,label: String,weight: Float=1f,action: ()->Unit) {
        val view=TextView(context).apply {
            id=View.generateViewId(); text=label; gravity=Gravity.CENTER
            setTextColor(TvStyle.text); textSize=20f; includeFontPadding=false
            background=TvStyle.shape(context,TvStyle.surface,Color.TRANSPARENT,8)
            isFocusable=true; isFocusableInTouchMode=true
            setOnFocusChangeListener { _,focused -> background=TvStyle.shape(context,if(focused) TvStyle.raised else TvStyle.surface,if(focused) TvStyle.accent else Color.TRANSPARENT,8) }
            setOnClickListener { action() }
        }
        row.addView(view,LayoutParams(0,dpi(50),weight).apply { marginEnd=dpi(6); topMargin=dpi(6) })
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
    fun linkDown(targetId: Int) { if(targetId==View.NO_ID) return; rows.lastOrNull()?.forEach { it.nextFocusDownId=targetId } }

    init {
        orientation=VERTICAL
        val functions=keyRow()
        addKey(functions,"搜索",1.6f) { onSearch() }
        addKey(functions,"清空",1.2f) { text=""; onChange(text) }
        addKey(functions,"删除",1.2f) { if(text.isNotEmpty()) { text=text.dropLast(1); onChange(text) } }
        addKey(functions,"推送",1.2f) { onPush() }
        listOf("ABCDEFG","HIJKLMN","OPQRSTU","VWXYZ").forEach { letters ->
            val row=keyRow()
            letters.forEach { letter -> addKey(row,letter.toString()) { char(letter) } }
        }
        listOf("01234","56789").forEach { digits ->
            val row=keyRow()
            digits.forEach { digit -> addKey(row,digit.toString()) { char(digit) } }
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
