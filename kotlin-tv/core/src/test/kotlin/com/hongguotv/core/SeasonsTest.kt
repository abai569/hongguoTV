package com.hongguotv.core

import org.junit.Assert.*
import org.junit.Test

class SeasonsTest {
    private fun series(id: String,title: String,badge: String="全 20 集")=Series(id,title,"https://example.com/$id.jpg","",badge,"剧情")

    @Test fun `same name entry without a season marker is unrelated when the first season exists`() {
        val rows=listOf(series("1","万妖图录传","全 60 集"),series("9","万妖图录传第一季","全 81 集"),
            series("2","万妖图录传第2季","全 90 集"),series("7","万妖图录传第7季",""))
        val picked=Seasons.pick("万妖图录传","3","万妖图录传第3季",rows)
        assertEquals(listOf("9","2","7"),picked.map { it.id })
        assertEquals("万妖图录传第一季  ·  全 81 集",Seasons.label("万妖图录传",picked[0].title,picked[0].badge))
    }

    @Test fun `bare title becomes the first season when no explicit first season exists`() {
        val rows=listOf(series("1","我合成了全世界","全 60 集"),series("2","我合成了全世界第2季"),series("3","我合成了全世界第3季"))
        val picked=Seasons.pick("我合成了全世界","2","我合成了全世界第2季",rows)
        assertEquals(listOf("1","3"),picked.map { it.id })
        assertEquals("第1季 · 我合成了全世界  ·  全 60 集",Seasons.label("我合成了全世界",picked[0].title,picked[0].badge))
        assertEquals("我合成了全世界第3季  ·  全 20 集",Seasons.label("我合成了全世界",picked[1].title,picked[1].badge))
    }

    @Test fun `chinese and arabic spellings of one season collapse`() {
        val rows=listOf(series("9","万妖图录传第一季","全 81 集"),series("7a","万妖图录传第7季",""),series("7b","万妖图录传第七季","全 90 集"))
        assertEquals(listOf("9","7a"),Seasons.pick("万妖图录传","3","万妖图录传第3季",rows).map { it.id })
    }

    @Test fun `current season is excluded`() {
        val rows=listOf(series("1","我合成了全世界","全 60 集"),series("2","我合成了全世界第2季"))
        assertEquals(listOf("1"),Seasons.pick("我合成了全世界","2","我合成了全世界第2季",rows).map { it.id })
        assertEquals(listOf("2"),Seasons.pick("我合成了全世界","1","我合成了全世界",rows).map { it.id })
    }

    @Test fun `chinese arabic markers and bare counters are parsed`() {
        assertEquals("我合成了全世界",Seasons.base("我合成了全世界第2季"))
        assertEquals("我合成了全世界",Seasons.base("我合成了全世界 第二部"))
        assertEquals("我合成了全世界",Seasons.base("我合成了全世界2"))
        assertEquals(12,Seasons.number("我合成了全世界第12季"))
        assertEquals(2,Seasons.number("我合成了全世界第二季"))
        assertEquals(0,Seasons.number("我合成了全世界"))
        assertEquals(2,Seasons.of("我合成了全世界","我合成了全世界2"))
        assertEquals(3,Seasons.of("我合成了全世界","我合成了全世界三"))
        assertEquals(0,Seasons.of("我合成了全世界","我合成了全世界"))
        assertEquals(0,Seasons.of("我合成了全世界","完全另一部剧"))
        assertEquals(1,Seasons.currentSeason("我合成了全世界","我合成了全世界"))
        assertEquals(2,Seasons.currentSeason("我合成了全世界","我合成了全世界第2季"))
    }

    @Test fun `chinese queries require the title to really contain them`() {
        assertTrue(Seasons.matchesQuery("万妖图录传","万妖图录传第八季"))
        assertFalse(Seasons.matchesQuery("万妖图录传","重生之我在后宫当妃子"))
        assertTrue(Seasons.matchesQuery("WHCLQSJ","完全无关的标题"))
    }

    @Test fun `unrelated titles are dropped`() {
        val rows=listOf(series("1","我合成了全世界","全 60 集"),series("5","毫不相关的剧"))
        assertEquals(listOf("1"),Seasons.pick("我合成了全世界","2","我合成了全世界第2季",rows).map { it.id })
    }
}
