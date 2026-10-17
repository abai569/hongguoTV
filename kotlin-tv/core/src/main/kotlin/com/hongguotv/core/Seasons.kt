// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core

/**
 * Season parsing and ordering for 查找其他季.
 *
 * 只有带「第X季/部」或「剧名+数字」的剧名才算得出季号。没有季号的同名条目（例如「万妖图录传」）分两种：
 * 已经能搜到明确的「第一季」时，它多半是「同名杂项」，直接剔除；完全找不到带季号的季时，才当作未标季的第一季。
 */
object Seasons {
    private val marker=Regex("第([0-9一二三四五六七八九十百两零]+)[季部]")
    private val noise=Regex("[\\s·:：、,.。\\-—()（）\\[\\]【】!！?？\"'“”‘’]")
    private val trailingDigits=Regex("[0-9]+$")
    private val numerals=listOf("一","二","三","四","五","六","七","八","九","十","十一","十二","十三","十四","十五","十六","十七","十八","十九","二十")
    fun norm(value: String)=value.replace(noise,"").lowercase()
    /** 1 -> 一, 12 -> 十二; 超过 20 退回阿拉伯数字。补搜缺失季名时按已有剧名的写法挑一个。 */
    fun numeral(number: Int)=numerals.getOrNull(number-1) ?: number.toString()
    /** 搜索命中判断：汉字查询要求标题里真的含这几个字（接口带纠错联想，容易混进无关剧）。 */
    fun matchesQuery(keyword: String,title: String): Boolean {
        val query=keyword.trim()
        if(query.isEmpty() || query.none { it.code>0x7f }) return true
        return norm(title).contains(norm(query))
    }
    /** 去掉季号与结尾数字后的剧名。 */
    fun base(title: String): String {
        var t=title.trim()
        t=t.replace(marker,"").trim()
        t=t.replace(trailingDigits,"").trim()
        t=t.trimEnd('季','部',' ','　','·','-','—',':','：')
        return t.ifBlank { title.trim() }
    }
    fun chineseNumber(value: String): Int {
        if(value.all { it.isDigit() }) return value.toIntOrNull() ?: 0
        val map=mapOf('零' to 0,'一' to 1,'二' to 2,'两' to 2,'三' to 3,'四' to 4,'五' to 5,'六' to 6,'七' to 7,'八' to 8,'九' to 9)
        var total=0; var number=0
        for(c in value) when {
            c=='百' -> { total+=(if(number==0) 1 else number)*100; number=0 }
            c=='十' -> { total+=(if(number==0) 1 else number)*10; number=0 }
            map.containsKey(c) -> number=map.getValue(c)
            else -> {}
        }
        return total+number
    }
    fun number(title: String): Int {
        val m=marker.find(title) ?: return 0
        return chineseNumber(m.groupValues[1])
    }
    /** 「第X季/部」或「剧名+数字」；裸名不算季号（返回 0）。 */
    fun of(baseTitle: String,title: String): Int {
        val explicit=number(title)
        if(explicit>0) return explicit
        val rest=norm(title).removePrefix(norm(baseTitle))
        if(rest.isEmpty()) return 0
        if(rest.length<=2 && rest.all { it.isDigit() }) return rest.toIntOrNull() ?: 0
        if(rest.length==1) return chineseNumber(rest)
        return 0
    }
    /** 当前剧集自己的季号：显式季号优先，裸名当第一季（只用于判断「当前这季」）。 */
    fun currentSeason(baseTitle: String,currentTitle: String): Int {
        val n=of(baseTitle,currentTitle)
        if(n>0) return n
        return if(norm(currentTitle)==norm(baseTitle)) 1 else 0
    }
    /** 「查找其他季」与搜索结果共用的排序：先按季号（没有季号的排最前），再按标题。 */
    fun order(baseTitle: String,rows: List<Series>): List<Series> = rows.sortedWith(compareBy({ of(baseTitle,it.title) }, { it.title }))
    fun pick(baseTitle: String,currentId: String,currentTitle: String,rows: List<Series>): List<Series> {
        val nb=norm(baseTitle)
        val candidates=rows.filter { it.id!=currentId && nb.isNotEmpty() && norm(it.title).contains(nb) }
        val numbered=candidates.mapNotNull { of(baseTitle,it.title).takeIf { n -> n>0 } }.toSet()
        val current=currentSeason(baseTitle,currentTitle)
        val seen=mutableSetOf<Int>(); val picked=mutableListOf<Pair<Int,Series>>()
        candidates.forEach { series ->
            var season=of(baseTitle,series.title)
            if(season==0) {
                // 已经有明确的「第一季」→ 这条没季号的同名剧不是本系列（同名杂项）；否则当成未标季的第一季
                if(1 in numbered) return@forEach
                season=1
            }
            if(current>0 && season==current) return@forEach
            if(seen.add(season)) picked.add(season to series)
        }
        return picked.sortedWith(compareBy({ it.first },{ it.second.title })).map { it.second }
    }
    /** 标签：没写季号的第一季补上「第1季 ·」，其余原样。 */
    fun label(baseTitle: String,title: String,badge: String): String {
        val head=if(of(baseTitle,title)==0 && norm(title)==norm(baseTitle)) "第1季 · $title" else title
        return "$head  ·  ${if(badge.isBlank()) "未更新" else badge}"
    }
}
