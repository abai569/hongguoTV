package com.hongguotv.core

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ContentRepositoryTest {
    private fun streamRepository(vararg variants: Triple<Int,String,String>) = repository { request ->
        if(request.url.host=="example.com") {
            val rows=JSONObject()
            variants.forEachIndexed { index,(height,codec,url) -> rows.put("video_$index",JSONObject().put("vheight",height).put("codec_type",codec).put("main_url",url)) }
            JSONObject().put("video_info",JSONObject().put("data",JSONObject().put("video_list",rows))).toString()
        } else """{"data":{"123":{"video_model":{"fallback_api":"https://example.com/stream"}}}}"""
    }

    @Test fun incompatibleByteVc2DoesNotBecomeAudioOnlyPlayback() {
        val repo=streamRepository(Triple(720,"bytevc2","https://example.com/unsupported"),Triple(1080,"bytevc1","https://example.com/hevc"))
        val result=repo.stream("123",720)
        assertEquals("https://example.com/hevc",result.url)
        assertEquals("1080P（兼容资源）",result.quality)
    }

    @Test fun compatibleLowerQualityWinsBeforeHigherResolutionFallback() {
        val repo=streamRepository(Triple(720,"bytevc2","https://example.com/unsupported"),Triple(480,"h264","https://example.com/avc"),Triple(1080,"bytevc1","https://example.com/hevc"))
        assertEquals("480P",repo.stream("123",720).quality)
    }

    @Test fun entirelyIncompatibleSourcesFallBackToWebsiteMp4() {
        val repo=streamRepository(Triple(720,"ByteVC2","https://example.com/unsupported"),Triple(480,"bvc2","https://example.com/unsupported2"))
        assertEquals("https://hongguoduanju.com/video/public/123/360p.mp4",repo.stream("123",720).url)
    }

    @Test fun legacyMissingCodecLabelKeepsQualityLimit() {
        val repo=streamRepository(Triple(720,"","https://example.com/legacy"),Triple(1080,"bytevc1","https://example.com/hevc"))
        assertEquals("720P",repo.stream("123",720).quality)
    }

    private fun repository(clock: () -> Long = System::currentTimeMillis, respond: (Request) -> String) = ContentRepository(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(respond(chain.request()).toResponseBody()).build()
        }.build(),clock
    )

    private fun rankPayload(page: Int = 1, totalPages: Int = 2) = JSONObject()
        .put("isSuccess",true)
        .put("rankList",JSONArray().put(JSONObject().put("seriesId","123").put("title","测试漫剧")
            .put("cover","https:\\u002F\\u002Fexample.com\\u002Fcover.jpg")
            .put("episodeCount",12).put("tags",JSONArray().put("玄幻"))))
        .put("pagination",JSONObject().put("pageNum",page).put("totalPages",totalPages))

    private fun script(name: String, args: JSONArray) =
        "<script data-fn-name=\"$name\" data-fn-args=\"${args.toString().replace("&","&amp;").replace("\"","&quot;")}\"></script>"

    private fun currentRank(payload: JSONObject) = currentRank("rank_hot-comic-drama/page",payload)

    private fun currentRank(route: String,payload: JSONObject) = script("mergeLoaderData",JSONArray()
        .put(route).put(JSONArray().put(JSONObject().put("key","content")
            .put("routerDataFnName","p").put("routerDataFnArgs",JSONArray().put(payload.toString())))))

    @Test fun rankingReadsEachHotSectionFromItsOwnRankPage() {
        val paths=mutableListOf<String>()
        val repo=repository { request ->
            paths+=request.url.encodedPath
            val route=when {
                request.url.encodedPath.contains("hot-real-drama") -> "rank_hot-real-drama/page"
                request.url.encodedPath.contains("hot-drama") -> "rank_hot-drama/page"
                else -> "rank_hot-comic-drama/page"
            }
            """<script>_ROUTER_DATA={"loaderData":{"$route":{"rankKey":"$route","pageNum":1}}};</script>"""+currentRank(route,rankPayload(1,1))
        }
        assertEquals("测试漫剧",repo.ranking(RankType.SHORT,1).items.single().title)
        assertEquals("测试漫剧",repo.ranking(RankType.COMIC,1).items.single().title)
        assertEquals("测试漫剧",repo.ranking(RankType.REAL,1).items.single().title)
        assertEquals(listOf("/rank/hot-drama","/rank/hot-comic-drama","/rank/hot-real-drama"),paths)
    }

    @Test fun homeCategoriesReturnThreeSectionsInWebsiteOrder() {
        val repo=repository { request ->
            val route=when {
                request.url.encodedPath.contains("hot-real-drama") -> "rank_hot-real-drama/page"
                request.url.encodedPath.contains("hot-drama") -> "rank_hot-drama/page"
                else -> "rank_hot-comic-drama/page"
            }
            currentRank(route,rankPayload(1,2))
        }
        val sections=repo.homeCategories()
        assertEquals(listOf(RankType.SHORT,RankType.COMIC,RankType.REAL),sections.map { it.first })
        assertTrue(sections.all { it.second.items.isNotEmpty() })
    }

    @Test fun rankingRetriesWhenRankPageReturnsLazyPlaceholder() {
        var calls=0
        val lazy="""<script>_ROUTER_DATA={"loaderData":{"rank_hot-drama/page":{"rankKey":"hongguo","pageNum":1}}};</script>"""
        val repo=repository {
            calls++
            if(calls==1) lazy else lazy+currentRank("rank_hot-drama/page",rankPayload(1,1))
        }
        assertEquals("测试漫剧",repo.ranking(RankType.SHORT,1).items.single().title)
        assertEquals(2,calls)
    }

    @Test fun homeCategoriesSkipsOneFailingSectionInsteadOfBlankHome() {
        val lazy="""<script>_ROUTER_DATA={"loaderData":{"rank_hot-comic-drama/page":{"rankKey":"comic","pageNum":1}}};</script>"""
        val repo=repository { request ->
            when {
                request.url.encodedPath.contains("hot-real-drama") -> currentRank("rank_hot-real-drama/page",rankPayload(1,1))
                request.url.encodedPath.contains("hot-drama") -> currentRank("rank_hot-drama/page",rankPayload(1,1))
                else -> lazy
            }
        }
        val sections=repo.homeCategories()
        assertEquals(listOf(RankType.SHORT,RankType.REAL),sections.map { it.first })
    }

    @Test fun comicRankingPreservesSourceRankHeatAndUpdateRatherThanRenumbering() {
        val payload=rankPayload(2,5)
        payload.getJSONArray("rankList").getJSONObject(0).put("rank",23).put("heatText","5746万热度")
        val repo=repository { request ->
            assertEquals("/rank/hot-comic-drama",request.url.encodedPath)
            assertEquals("2",request.url.queryParameter("page"))
            """<script>_ROUTER_DATA={"loaderData":{"rank_hot-comic-drama/page":{"rankKey":"comic","pageNum":2,"updatedText":"9月22日已更新"}}};</script>"""+currentRank(payload)
        }
        val result=repo.comicRanking(2)
        assertNotNull(result.ranking)
        assertEquals(RankPosition(23,"5746万热度"),result.ranking!!.positions["123"])
        assertEquals(5,result.ranking.totalPages)
        assertEquals("9月22日已更新",result.ranking.updatedText)
        assertTrue(result.hasMore)
        assertFalse(result.items.single().toJson().has("rank"))
    }

    @Test fun missingRankIsNotInventedAndLastPageStops() {
        val result=repository { currentRank(rankPayload(5,5)) }.comicRanking(5)
        assertNotNull(result.ranking)
        assertEquals(RankPosition(null,""),result.ranking!!.positions["123"])
        assertFalse(result.hasMore)
    }

    @Test fun renderedRankingPreservesPageTwoRankAndHeat() {
        val repo=repository {
            """<script>_ROUTER_DATA={"loaderData":{"rank_hot-comic-drama/page":{"rankKey":"comic","pageNum":2,"updatedText":"今日已更新"}}};</script>
                <article aria-labelledby="rank-title-123"><span class="pc-badge-number-example">21</span>
                <h2 id="rank-title-123">漫剧</h2><p class="pc-metrics-example"><span>5746万热度</span></p></article>
                <nav aria-label="榜单分页"><a href="/rank/hot-comic-drama">1</a><span aria-current="page">2</span><a href="/rank/hot-comic-drama?page=5">5</a></nav>"""
        }
        val result=repo.comicRanking(2)
        assertNotNull(result.ranking)
        assertEquals(RankPosition(21,"5746万热度"),result.ranking!!.positions["123"])
        assertEquals(5,result.ranking.totalPages)
        assertEquals("今日已更新",result.ranking.updatedText)
    }

    @Test fun rankingRejectsUnexpectedPageInsteadOfShowingWrongRanks() {
        val repo=repository { currentRank(rankPayload(1,5)) }
        assertThrows(IOException::class.java) { repo.comicRanking(2) }
        assertThrows(IllegalArgumentException::class.java) { repo.comicRanking(0) }
    }

    @Test fun renderedLastPageCountsCurrentPageWithoutAnAnchor() {
        val repo=repository {
            """<script>_ROUTER_DATA={"loaderData":{"rank_hot-comic-drama/page":{"rankKey":"comic","pageNum":5}}};</script>
                <article aria-labelledby="rank-title-123"><span class="pc-badge-number-example">81</span><h2 id="rank-title-123">漫剧</h2></article>
                <nav aria-label="榜单分页"><a href="/rank/hot-comic-drama?page=4">4</a><span aria-current="page">5</span></nav>"""
        }
        val result=repo.comicRanking(5)
        assertEquals(5,result.ranking!!.totalPages)
        assertFalse(result.hasMore)
    }

    @Test fun comicHomeUsesDedicatedRankAndRealPagination() {
        val repo=repository { request ->
            assertEquals("/rank/hot-comic-drama",request.url.encodedPath)
            assertEquals("2",request.url.queryParameter("page"))
            currentRank(rankPayload(2,2))
        }
        val page=repo.home(2,ContentType.COMIC)
        assertEquals("测试漫剧",page.items.single().title)
        assertEquals("https://example.com/cover.jpg",page.items.single().cover)
        assertEquals("全 12 集",page.items.single().badge)
        assertFalse(page.hasMore)
    }

    @Test fun legacyRankIsStillSupported() {
        val repo=repository { script("r",JSONArray().put("rank_hot-comic-drama/page").put("content").put(rankPayload())) }
        assertTrue(repo.home(1,ContentType.COMIC).hasMore)
    }

    @Test fun comicHomeDoesNotUseUnrelatedRankData() {
        val repo=repository { script("r",JSONArray().put("rank_hot-real-drama/page").put("content").put(rankPayload())) }
        assertThrows(IOException::class.java) { repo.home(1,ContentType.COMIC) }
    }

    @Test fun rejectedComicRankRemainsAnError() {
        val repo=repository { currentRank(rankPayload().put("isSuccess",false)) }
        assertThrows(IOException::class.java) { repo.home(1,ContentType.COMIC) }
    }

    @Test fun renderedComicRankWorksWhenStreamedPayloadIsMissing() {
        val repo=repository {
            """<script>_ROUTER_DATA={"loaderData":{"rank_hot-comic-drama/page":{"rankKey":"comic","pageNum":2}}};</script>
                <article aria-labelledby="rank-title-123"><a href="/detail?series_id=123"><img src="https://example.com/a.jpg"></a>
                <h2 id="rank-title-123">测试 &amp; 漫剧</h2><p class="pc-description-example">简介</p><p class="pc-metrics-example">123万热度</p></article>
                <nav aria-label="榜单分页"><a href="/rank/hot-comic-drama?page=1">1</a><span aria-current="page">2</span><a href="/rank/hot-comic-drama?page=3">3</a></nav>"""
        }
        val page=repo.home(2,ContentType.COMIC)
        assertEquals("测试 & 漫剧",page.items.single().title)
        assertEquals("https://example.com/a.jpg",page.items.single().cover)
        assertTrue(page.hasMore)
    }

    @Test fun comicSearchCarriesCursorAndKeepsTypesAndQueriesSeparate() {
        val requests=mutableListOf<Request>()
        val repo=repository { request ->
            requests+=request
            JSONObject().put("code",0).put("search_tabs",JSONArray().put(searchTab(19,"999")
                .put("search_id","comic-session").put("passback","comic-cursor").put("next_offset",25))).toString()
        }
        repo.search("修仙",1,ContentType.COMIC)
        repo.search("修仙",2,ContentType.COMIC)
        assertEquals("comic-session",requests[1].url.queryParameter("search_id"))
        assertEquals("comic-cursor",requests[1].url.queryParameter("passback"))
        assertEquals("25",requests[1].url.queryParameter("offset"))
        assertThrows(SearchSessionExpiredException::class.java) { repo.search("重生",2,ContentType.COMIC) }
        assertEquals(2,requests.size)
    }

    @Test fun expiredCursorRequestsFreshSearchInsteadOfDuplicateOffsetPage() {
        var now=0L
        var requests=0
        val repo=repository(clock={ now }) {
            requests++
            JSONObject().put("code",0).put("search_tabs",JSONArray().put(searchTab(19,"999")
                .put("search_id","session").put("passback","cursor"))).toString()
        }
        repo.search("修仙",1,ContentType.COMIC)
        now=300001L
        assertThrows(SearchSessionExpiredException::class.java) { repo.search("修仙",2,ContentType.COMIC) }
        assertEquals(1,requests)
        repo.search("修仙",1,ContentType.COMIC)
        repo.search("修仙",2,ContentType.COMIC)
        assertEquals(3,requests)
    }

    @Test fun shortHomeStillUsesCategoryAndStopsOnLastPage() {
        val repo=repository { request ->
            assertEquals("1",request.url.queryParameter("tab"))
            """<script>_ROUTER_DATA={"loaderData":{"category_$":{"recommendList":[{"series_id":"456","series_name":"测试短剧"}],"pagination":{"totalPages":1}}}};</script>"""
        }
        val result=repo.home(1,ContentType.SHORT)
        assertEquals("456",result.items.single().id)
        assertFalse(result.hasMore)
    }

    private fun searchTab(type: Int,id: String,title: String="修仙测试剧集") = JSONObject().put("tab_type",type).put("has_more",true)
        .put("data",JSONArray().put(JSONObject().put("video_data",JSONArray().put(JSONObject()
            .put("series_id",id).put("title",title).put("episode_cnt",8)))))

    @Test fun comicSearchRequestsAndSelectsOnlyComicTab() {
        var requests=0
        val repo=repository { request ->
            requests++
            assertEquals("19",request.url.queryParameter("tab_type"))
            assertEquals(if(requests==1) "0" else "20",request.url.queryParameter("offset"))
            assertEquals("修仙",request.url.queryParameter("query"))
            JSONObject().put("code",0).put("search_tabs",JSONArray()
                .put(searchTab(11,"111")).put(searchTab(19,"999"))).toString()
        }
        repo.search("修仙",1,ContentType.COMIC)
        assertEquals(listOf("999"),repo.search("修仙",2,ContentType.COMIC).items.map { it.id })
    }

    @Test fun emptyComicSearchDoesNotFallBackToShortResults() {
        var requests=0
        val repo=repository { requests++; """{"code":0,"search_tabs":[{"tab_type":19,"data":[],"has_more":false}]}""" }
        assertTrue(repo.search("无结果",1,ContentType.COMIC).items.isEmpty())
        assertEquals(1,requests)
    }

    @Test fun missingComicTabFallsBackToWebsiteSearch() {
        var requests=0
        val repo=repository { requests++
            if(requests==1) """{"code":0,"search_tabs":[{"tab_type":11,"data":[]}]}"""
            else """<script>_ROUTER_DATA={"loaderData":{"search_page":{"searchList":[{"series_id":"789","series_name":"修仙漫剧"}]}}}};</script>"""
        }
        assertEquals("789",repo.search("修仙",1,ContentType.COMIC).items.single().id)
        assertEquals(2,requests)
    }

    @Test fun shortSearchKeepsWebsiteFallback() {
        val paths=mutableListOf<String>()
        val repo=repository { request ->
            paths+=request.url.encodedPath
            if(paths.size==1) throw IOException("offline")
            """<script>_ROUTER_DATA={"loaderData":{"search_page":{"searchList":[{"series_id":"456","series_name":"测试短剧"}]}}}};</script>"""
        }
        assertEquals("456",repo.search("短剧",1,ContentType.SHORT).items.single().id)
        assertEquals(2,paths.size)
    }

    @Test fun initialsSearchUsesSuggestedTitleWhenRawQueryHasNoResults() {
        val queries=mutableListOf<String>()
        val repo=repository { request ->
            queries += request.url.queryParameter("query") ?: ""
            when {
                request.url.encodedPath.contains("incent_resource/suggestion") ->
                    """{"suggest_list":[{"name":"我合成了全世界"}]}"""
                request.url.queryParameter("query") == "WHCLQSJ" ->
                    """{"code":0,"search_tabs":[{"tab_type":11,"data":[],"has_more":false}]}"""
                else -> """{"code":0,"search_tabs":[${searchTab(11,"321","我合成了全世界")}]}"""
            }
        }
        assertEquals("321",repo.search("WHCLQSJ",1,ContentType.SHORT).items.single().id)
        assertEquals(listOf("WHCLQSJ","WHCLQSJ","我合成了全世界"),queries)
    }

    @Test fun searchSuggestUsesWebsiteSuggestionEndpoint() {
        val repo=repository { request ->
            assertEquals("/incent_resource/suggestion",request.url.encodedPath)
            assertEquals("whclqsj",request.url.queryParameter("query"))
            assertEquals("10",request.url.queryParameter("count"))
            assertEquals("8662",request.url.queryParameter("app_id"))
            """{"suggest_list":[{"name":"我合成了全世界第1季","keyword":"7689289660794670105"}]}"""
        }
        assertEquals(listOf("我合成了全世界第1季"),repo.searchSuggest("whclqsj"))
    }

    @Test fun comicSearchFallsBackToWebsiteWhenAppSearchReturnsEmptyBody() {
        val paths=mutableListOf<String>()
        val repo=repository { request ->
            paths+=request.url.encodedPath
            if(paths.size==1) ""
            else """<script>_ROUTER_DATA={"loaderData":{"search_page":{"searchList":[{"series_id":"789","series_name":"测试漫剧"}]}}}};</script>"""
        }
        assertEquals("789",repo.search("漫剧",1,ContentType.COMIC).items.single().id)
        assertEquals(2,paths.size)
    }

    @Test fun chineseQueryDropsUnrelatedTitles() {
        val repo=repository { request ->
            JSONObject().put("code",0).put("search_tabs",JSONArray().put(JSONObject().put("tab_type",11).put("has_more",false)
                .put("data",JSONArray()
                    .put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","1").put("title","万妖图录传第八季").put("episode_cnt",109))))
                    .put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","2").put("title","重生之我在后宫当妃子").put("episode_cnt",16)))))))
                .toString()
        }
        assertEquals(listOf("1"),repo.search("万妖图录传",1,ContentType.SHORT).items.map { it.id })
    }

    @Test fun searchAllMergesSeasonsTheAppIndexMisses() {
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion"))
                """{"suggest_list":[{"name":"万妖图录传第二季","keyword":"222","video_data":{"series_intro":"明月","episode_right_text":"全53集"}},{"name":"万妖图录传AI真人版","keyword":"10_abc"}]}"""
            else """{"code":0,"search_tabs":[{"tab_type":11,"data":[],"has_more":false},{"tab_type":19,"data":[],"has_more":false}]}"""
        }
        val items=repo.searchAll("万妖图录传",1).items
        assertEquals(listOf("222"),items.map { it.id })
        assertEquals("全53集",items.single().badge)
    }

    @Test fun searchAllProbesSeasonsTheSuggestIndexOnlyReturnsCurrentOne() {
        var probes=0
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) {
                if(request.url.queryParameter("count")=="8") {
                    probes++
                    """{"suggest_list":[{"word_type":"short_play_name","name":"合成测试剧第二季","keyword":"222","video_data":{"episode_right_text":"全10集"}},{"word_type":"short_play_name","name":"合成测试剧第三季","keyword":"333","video_data":{"episode_right_text":"全20集"}}]}"""
                } else """{"suggest_list":[{"word_type":"short_play_name","name":"合成测试剧","keyword":"111","video_data":{"episode_right_text":"全8集"}}]}"""
            } else """{"code":0,"search_tabs":[{"tab_type":11,"data":[],"has_more":false},{"tab_type":19,"data":[],"has_more":false}]}"""
        }
        val items=repo.searchAll("合成测试剧",1).items
        assertEquals(listOf("111","222","333"),items.map { it.id })
        assertTrue("搜索首页应按缺失季号补搜",probes>0)
    }

    @Test fun searchAllSkipsSeasonProbesWhenResultsAreAlreadyRich() {
        var probes=0
        val rows=JSONArray()
        (1..8).forEach { index ->
            rows.put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","$index").put("title","合成测试剧第${index}季").put("episode_cnt",10))))
        }
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) {
                if(request.url.queryParameter("count")=="8") probes++
                """{"suggest_list":[]}"""
            } else JSONObject().put("code",0).put("search_tabs",JSONArray().put(JSONObject().put("tab_type",11).put("has_more",false).put("data",rows))).toString()
        }
        assertEquals(8,repo.searchAll("合成测试剧",1).items.size)
        assertEquals(0,probes)
    }

    @Test fun searchSeasonFamilyReadsSecondSourcePage() {
        var searches=0
        val firstPage=JSONArray().put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","111").put("title","合成测试剧第一季").put("episode_cnt",10))))
        val secondPage=JSONArray().put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","222").put("title","合成测试剧第二季").put("episode_cnt",20))))
        fun tabs(data: JSONArray,hasMore: Boolean)=JSONObject().put("code",0).put("search_tabs",JSONArray()
            .put(JSONObject().put("tab_type",11).put("has_more",hasMore).put("data",data))
            .put(JSONObject().put("tab_type",19).put("has_more",hasMore).put("data",data))).toString()
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) """{"suggest_list":[]}"""
            else { searches++; if(searches==1) tabs(firstPage,true) else tabs(secondPage,false) }
        }
        val family=repo.searchSeasonFamily("合成测试剧",1)
        assertEquals(listOf("111","222"),family.map { it.id })
        assertTrue("补搜种子应读到源搜索第 2 页",searches>=2)
    }

    @Test fun searchShowsEverythingInOnePage() {
        val rows=JSONArray().put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","1").put("title","末世靠山王第一季").put("episode_cnt",67))))
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) """{"suggest_list":[]}"""
            else JSONObject().put("code",0).put("search_tabs",JSONArray()
                .put(JSONObject().put("tab_type",11).put("has_more",true).put("data",rows))
                .put(JSONObject().put("tab_type",19).put("has_more",true).put("data",rows))).toString()
        }
        val first=repo.searchAll("末世靠山王",1)
        assertEquals(1,first.items.size)
        assertFalse("搜索不再分页",first.hasMore)
        assertEquals(1,repo.searchAll("末世靠山王",3).items.size)
    }

    @Test fun searchResultsOrderSeasonsByNumber() {
        val rows=JSONArray()
        listOf("222" to "合成测试剧第二季","333" to "合成测试剧第十季","111" to "合成测试剧第一季","444" to "合成测试剧").forEach { (id,title) ->
            rows.put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id",id).put("title",title).put("episode_cnt",10))))
        }
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) """{"suggest_list":[]}"""
            else JSONObject().put("code",0).put("search_tabs",JSONArray()
                .put(JSONObject().put("tab_type",11).put("has_more",false).put("data",rows))
                .put(JSONObject().put("tab_type",19).put("has_more",false).put("data",rows))).toString()
        }
        assertEquals(listOf("444","111","222","333"),repo.searchAll("合成测试剧",1).items.map { it.id })
    }

    @Test fun searchDropsSeasonsWithoutEpisodeCount() {
        // 建议接口里只有剧名、没有集数的季（弹窗里只能显示「· 查看剧集」）不进结果
        val rows=JSONArray().put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","111").put("title","合成测试剧").put("episode_cnt",8))))
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) """{"suggest_list":[{"word_type":"short_play_name","name":"合成测试剧第二季","keyword":"222"},{"word_type":"short_play_name","name":"合成测试剧第三季","keyword":"333","video_data":{"episode_right_text":"全20集"}}]}"""
            else JSONObject().put("code",0).put("search_tabs",JSONArray()
                .put(JSONObject().put("tab_type",11).put("has_more",false).put("data",rows))).toString()
        }
        assertEquals(listOf("111","333"),repo.searchAll("合成测试剧",1).items.map { it.id })
    }

    @Test fun searchKeepsResultsWhenNoEpisodeCountIsKnown() {
        // 全都拿不到集数时不能把结果清空，否则会误报「没有找到相关结果」
        val rows=JSONArray().put(JSONObject().put("video_data",JSONArray().put(JSONObject().put("series_id","555").put("title","合成测试剧第二季"))))
        val repo=repository { request ->
            if(request.url.encodedPath.contains("incent_resource/suggestion")) """{"suggest_list":[]}"""
            else JSONObject().put("code",0).put("search_tabs",JSONArray()
                .put(JSONObject().put("tab_type",11).put("has_more",false).put("data",rows))).toString()
        }
        assertEquals(listOf("555"),repo.searchAll("合成测试剧",1).items.map { it.id })
    }
}
