// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*

/** A remote-accessible side panel; selecting a number is the only action that changes episodes. */
class EpisodePanel(
    private val activity: Activity,private val count: Int,private val current: Int,
    private val locate: (Int,View,(Int)->Unit)->Unit,
    private val select: (Int)->Unit,
    private val otherSeasons: ()->Unit,
    private val closed: ()->Unit
) {
    private val dialog=Dialog(activity)
    private val groupSize=100
    private var group=current/groupSize
    private var focused=current
    private fun dp(n: Int)=(n*activity.resources.displayMetrics.density).toInt()
    private fun label(value: String,size: Float=17f)=TextView(activity).apply { text=value; textSize=size; setTextColor(TvStyle.text); setPadding(dp(8),dp(6),dp(8),dp(6)) }
    private fun button(value: String,active: Boolean=false,action: ()->Unit)=label(value,16f).apply {
        id=View.generateViewId(); gravity=Gravity.CENTER; minHeight=dp(46); isFocusable=true; isFocusableInTouchMode=true
        fun paint(focus: Boolean) { background=GradientDrawable().apply { setColor(if(focus) TvStyle.accent else if(active) Color.rgb(61,39,34) else TvStyle.surface); cornerRadius=dp(6).toFloat() } }
        paint(false); setOnFocusChangeListener { _,hasFocus -> paint(hasFocus) }; setOnClickListener { action() }
    }
    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setOnDismissListener { closed() }
        render(); dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
            attributes=attributes.apply { x=(activity.resources.displayMetrics.widthPixels*.05f).toInt() }
            setLayout(minOf(dp(520),(activity.resources.displayMetrics.widthPixels*.8f).toInt()),(activity.resources.displayMetrics.heightPixels*.9f).toInt())
        }
    }
    fun dismiss()=dialog.dismiss()
    private fun render() {
        val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12)); setBackgroundColor(TvStyle.background) }
        content.addView(label("选集  /  正在看第 ${current+1} 集",20f))
        val maxGroup=(count-1)/groupSize
        val actions=LinearLayout(activity)
        val other=button("其他季") { dismiss(); otherSeasons() }
        lateinit var jump: TextView
        jump=button("跳转集数") { locate(focused,jump) { target -> focused=target; group=target/groupSize; render() } }
        val close=button("关闭") { dismiss() }
        listOf(other,jump,close).forEach { actions.addView(it,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) }) }
        content.addView(actions)
        val groupRow=LinearLayout(activity)
        val groupButtons=mutableListOf<TextView>()
        for(g in 0..maxGroup) {
            val start=g*groupSize+1; val end=minOf((g+1)*groupSize,count)
            val item=button("$start-$end",g==group) { group=g; focused=g*groupSize; render() }
            groupButtons+=item
            groupRow.addView(item,LinearLayout.LayoutParams(-2,-2).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
        }
        val groupScroll=HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false }
        groupScroll.addView(groupRow)
        content.addView(groupScroll,LinearLayout.LayoutParams(-1,-2))
        content.addView(label("第 ${group*groupSize+1}—${minOf((group+1)*groupSize,count)} 集 / 共 $count 集",14f))
        val scroll=ScrollView(activity); val grid=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }; scroll.addView(grid)
        content.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val cells=mutableListOf<TextView>()
        (group*groupSize until minOf((group+1)*groupSize,count)).toList().chunked(5).forEach { indexes ->
            val row=LinearLayout(activity)
            indexes.forEach { index ->
                val cell=button("${index+1}",index==current) { select(index) }
                cell.contentDescription="第 ${index+1} 集"+(if(index==current) "，当前播放" else "")
                row.addView(cell,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) }); cells+=cell
            }
            repeat(5-indexes.size) { row.addView(Space(activity),LinearLayout.LayoutParams(0,1,1f)) }
            grid.addView(row)
        }
        cells.forEachIndexed { i,cell ->
            val rowStart=i/5*5; val rowEnd=minOf(rowStart+4,cells.lastIndex)
            cell.nextFocusLeftId=cells[if(i==rowStart) rowEnd else i-1].id
            cell.nextFocusRightId=cells[if(i==rowEnd) rowStart else i+1].id
            cell.nextFocusUpId=if(i>=5) cells[i-5].id else (groupButtons.firstOrNull()?.id ?: (if(close.isFocusable) close.id else jump.id))
            cell.nextFocusDownId=cells[if(i+5<cells.size) i+5 else if(i/5<cells.lastIndex/5) cells.lastIndex else i].id
        }
        other.nextFocusLeftId=close.id; other.nextFocusRightId=jump.id
        jump.nextFocusLeftId=other.id; jump.nextFocusRightId=close.id
        close.nextFocusLeftId=jump.id; close.nextFocusRightId=other.id
        other.nextFocusDownId=groupButtons.firstOrNull()?.id ?: cells.first().id
        jump.nextFocusDownId=groupButtons.firstOrNull()?.id ?: cells.first().id
        close.nextFocusDownId=groupButtons.firstOrNull()?.id ?: cells.first().id
        groupButtons.forEachIndexed { i,item ->
            item.nextFocusLeftId=groupButtons[if(i==0) groupButtons.lastIndex else i-1].id
            item.nextFocusRightId=groupButtons[if(i==groupButtons.lastIndex) 0 else i+1].id
            item.nextFocusUpId=if(other.isFocusable) other.id else jump.id
            item.nextFocusDownId=cells.first().id
        }
        dialog.setContentView(content)
        cells[(focused-group*groupSize).coerceIn(0,cells.lastIndex)].requestFocus()
    }
}
