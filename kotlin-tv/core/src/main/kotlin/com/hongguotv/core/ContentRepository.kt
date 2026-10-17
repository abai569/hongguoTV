// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key, "").trim()
internal fun cleanUrl(s: String) = s.replace("\\/","/").replace("\\u002F","/",ignoreCase=true).replace("\\u0026","&").replace("&amp;","&")
data class Series(val id: String, val title: String, val cover: String = "", val description: String = "", val badge: String = "", val tags: String = "") {
    fun toJson() = JSONObject().put("id",id).put("title",title).put("cover",cover).put("description",description).put("badge",badge).put("tags",tags)
    companion object { fun fromJson(o: JSONObject) = Series(o.str("id"),o.str("title"),o.str("cover"),o.str("description"),o.str("badge"),o.str("tags")) }
}
data class Detail(val series: Series, val episodes: List<String>)
data class RankPosition(val rank: Int?, val heat: String)
data class ComicRanking(val positions: Map<String,RankPosition>, val totalPages: Int, val updatedText: String)
data class CatalogPage(val items: List<Series>, val hasMore: Boolean, val ranking: ComicRanking? = null)
data class StreamInfo(val url: String, val key: ByteArray?, val quality: String) {
    var debugRaw: String = ""
    var danmakuRaw: JSONArray = JSONArray()
    var episodeId: String = ""
    var durationMs: Long = 0L
    var codec: String = ""
}
class SearchSessionExpiredException: IOException("搜索结果已过期，请从第 1 页重新搜索")

class ContentRepository(val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build(), private val clock: () -> Long = System::currentTimeMillis) {
    private val signer = Signer()
    private val site = "https://hongguoduanju.com"
    /** Fresh synthetic identity per signed request, mirroring the desktop client, so a fixed device id is not throttled. */
    private fun freshDevice(): Map<String,String> {
        fun id()="1"+(1_000_000_000_000_000L+java.util.concurrent.ThreadLocalRandom.current().nextLong(0,900_000_000_000_000L)).toString()
        return LinkedHashMap(VendorConstants.device).apply { put("iid",id()); put("device_id",id()) }
    }
    private data class SearchCursor(val searchId: String, val passback: String, val offset: Int?, val updatedAt: Long)
    private val searchCursors=linkedMapOf<Triple<ContentType,String,Int>,SearchCursor>()
    private fun text(url: String, signed: Signer.Request? = null): String {
        val request = Request.Builder().url(url).header("User-Agent",VendorConstants.VIDEO_UA).header("Accept-Language","zh-CN,zh;q=0.9")
        signed?.headers?.forEach { (k,v) -> request.header(k,v) }
        signed?.body?.let { request.post(it.toRequestBody("application/json; charset=utf-8".toMediaType())) }
        return RequestScope.execute(http.newCall(request.build())) { response ->
            if (!response.isSuccessful) throw IOException("内容请求失败（HTTP ${response.code}）")
            val body = response.body ?: throw IOException("内容响应为空")
            if (body.contentLength() > 16*1024*1024) throw IOException("内容响应过大")
            val input=body.byteStream(); val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(out.size()<=16*1024*1024) { val n=input.read(buffer); if(n<0) break; out.write(buffer,0,n) }; val bytes=out.toByteArray()
            if(bytes.size > 16*1024*1024) throw IOException("内容响应过大")
            if(bytes.isEmpty()) throw IOException("Empty content response (HTTP ${response.code})")
            String(bytes,Charsets.UTF_8)
        }
    }
    private fun router(url: String) = extractRouter(text(url)).getJSONObject("loaderData")
    private fun card(item: JSONObject): Series {
        val o=item.optJSONObject("video_data") ?: item
        val count=o.optInt("episode_cnt",o.optJSONObject("series_episode_info")?.optInt("episode_cnt",0) ?: 0)
        val tags=o.optJSONArray("tags") ?: o.optJSONArray("category_list") ?: JSONArray()
        return Series(o.str("series_id"),o.str("series_name").ifEmpty { o.str("series_title").ifEmpty { item.str("name") } },cleanUrl(o.str("series_cover")),o.str("series_intro"),o.str("episode_right_text").ifEmpty { if(count>0) "全 ${count} 集" else "" },(0 until minOf(tags.length(),5)).joinToString(" · ") { tags.optJSONObject(it)?.str("name") ?: tags.optString(it) })
    }
    private fun valid(rows: List<Series>) = rows.filter { it.id.matches(Regex("[0-9]{1,30}")) && it.title.isNotBlank() }.distinctBy { it.id }
    fun ranking(type: RankType, page: Int = 1): CatalogPage {
        require(page in 1..100)
        // The website rank page alternates between an inline payload and a lazy placeholder with no
        // list; retry a few times so a cache-miss response does not blank the board or the home sections.
        var last: Exception? = null
        for(attempt in 1..4) {
            try { return ComicRank.parse(text("$site/rank/${type.slug}?page=$page"),page,type,allowRendered=(attempt==4)) }
            catch(e: Exception) { last=e; if(attempt<4) runCatching { Thread.sleep(200L*attempt) } }
        }
        throw (last as? IOException) ?: IOException("${type.label}榜单暂不可用")
    }
    fun comicRanking(page: Int = 1): CatalogPage = ranking(RankType.COMIC,page)
    /** First ranking page for each home "hot" section, in website order. */
    fun homeCategories(): List<Pair<RankType,CatalogPage>> {
        var last: Exception? = null
        val sections=RankType.entries.mapNotNull { type ->
            try { type to ranking(type,1) } catch(e: Exception) { last=e; null }
        }
        if(sections.isEmpty()) throw (last as? IOException) ?: IOException("首页热播暂不可用")
        return sections
    }
    fun home(page: Int = 1, type: ContentType = ContentType.SHORT): CatalogPage {
        require(page in 1..100)
        if(type==ContentType.COMIC) return comicRanking(page)
        val data=router("$site/category?tab=1&sort_type=1"+if(page>1) "&page=$page" else "")
        val section=data.optJSONObject("category_page") ?: data.optJSONObject("category_$") ?: throw IOException("首页数据结构已变化")
        val rows=section.optJSONArray("recommendList") ?: section.optJSONObject("categoryData")?.optJSONArray("recommendList") ?: throw IOException("首页数据暂不可用")
        val items=valid(rows.objects().map(::card))
        val lastPage=section.optJSONObject("pagination")?.optInt("totalPages",100) ?: 100
        return CatalogPage(items,items.isNotEmpty() && page<minOf(lastPage,100))
    }
    fun search(keyword: String, page: Int = 1, type: ContentType = ContentType.SHORT): CatalogPage {
        require(keyword.isNotBlank() && keyword.length<=80 && page in 1..100)
        try {
            val result=appSearch(keyword,page,type)
            if(result.items.isNotEmpty()) return result
            if(page==1 && isInitialsQuery(keyword)) {
                for(candidate in runCatching { searchSuggest(keyword) }.getOrDefault(emptyList()).take(5)) {
                    val suggested=runCatching { search(candidate,1,type) }.getOrNull()
                    if(suggested != null && suggested.items.isNotEmpty()) return suggested
                }
            }
            if(!result.hasMore) return result
        } catch (e: Exception) { if(e is SearchSessionExpiredException) throw e }
        // The website search does not expose a reliable content type. It is the fallback for
        // both tabs because the app search endpoint can return an empty body after API changes.
        val data=router("$site/search/${Signer.encode(keyword)}?page=$page")
        val section=data.optJSONObject("search_(keyword)/page") ?: data.optJSONObject("search_page") ?: throw IOException("搜索数据暂不可用")
        val rows=section.optJSONArray("searchList") ?: throw IOException("搜索数据结构已变化")
        val items=valid(rows.objects().map(::card)).filter { Seasons.matchesQuery(keyword,it.title) }
        return CatalogPage(items,items.isNotEmpty() && page<100)
    }
    private fun appSearch(keyword: String, page: Int, type: ContentType): CatalogPage {
        val values=VendorConstants.device.toMutableMap()
        for(k in listOf("version_code","manifest_version_code","update_version_code","pv_player")) values[k]="72232"
        values["version_name"]="7.2.2.32"
        values.putAll(linkedMapOf("query" to keyword,"count" to "20","offset" to ((page-1)*20).toString(),"tab_type" to type.searchTab.toString(),"bookshelf_search_plan" to "4","use_correct" to "true","user_is_login" to "0"))
        val cursor=synchronized(searchCursors) {
            val now=clock()
            searchCursors.entries.removeAll { now-it.value.updatedAt>300000 || (page==1 && it.key.first==type && it.key.second==keyword) }
            searchCursors[Triple(type,keyword,page-1)]
        }
        if(type==ContentType.COMIC && page>1 && cursor==null) throw SearchSessionExpiredException()
        cursor?.let {
            if(it.searchId.isNotBlank()) values["search_id"]=it.searchId
            if(it.passback.isNotBlank()) values["passback"]=it.passback
            it.offset?.takeIf { offset -> offset>=0 }?.let { offset -> values["offset"]=offset.toString() }
        }
        val signed=signer.sign("https://api5-normal-sinfonlinea.fqnovel.com/reading/bookapi/search/tab/v",values)
        val data=JSONObject(text(signed.url,signed))
        if(data.optInt("code",-1)!=0) throw IOException("搜索接口暂不可用")
        val tabs=data.optJSONArray("search_tabs") ?: data.optJSONObject("data")?.optJSONArray("search_tabs") ?: throw IOException("搜索格式异常")
        val tab=tabs.objects().firstOrNull { it.optInt("tab_type")==type.searchTab } ?: throw IOException("${type.label}搜索暂不可用")
        val list=mutableListOf<Series>()
        for(cell in (tab.optJSONArray("data") ?: JSONArray()).objects()) {
            val cells=mutableListOf(cell)
            runCatching { JSONObject(cell.str("lynx_data")).optJSONArray("cell_data")?.objects() }.getOrNull()?.let { cells.addAll(it) }
            for(row in cells) {
                val video=row.optJSONArray("video_data")?.objects()?.firstOrNull() ?: continue
                val id=video.str("series_id").ifEmpty { row.str("book_id").ifEmpty { row.str("search_result_id") } }
                val title=row.optJSONObject("search_high_light")?.optJSONObject("title")?.str("text").orEmpty().ifEmpty { video.str("title").ifEmpty { row.str("cell_name") } }
                val count=video.optInt("episode_cnt")
                list+=Series(id,title,cleanUrl(video.str("cover").ifEmpty { video.str("cover_url") }),video.str("video_desc"),if(count>0) "全 $count 集" else video.str("rec_text"),video.str("sub_title"))
            }
        }
        synchronized(searchCursors) {
            searchCursors[Triple(type,keyword,page)]=SearchCursor(tab.str("search_id"),tab.str("passback"),tab.str("next_offset").toIntOrNull(),clock())
            while(searchCursors.size>100) searchCursors.remove(searchCursors.keys.first())
        }
        return CatalogPage(valid(list).filter { Seasons.matchesQuery(keyword,it.title) },tab.optBoolean("has_more",false) && page<100)
    }
    fun searchAll(keyword: String, page: Int = 1): CatalogPage {
        // 搜索一页看全：源搜索是先分页后按标题过滤，翻页基本是空页，这里直接把前两页合并去重后一次列出
        val collected=LinkedHashMap<String,Series>()
        var answered=false
        for(sourcePage in 1..2) {
            var more=false
            for(type in listOf(ContentType.SHORT,ContentType.COMIC)) {
                val result=runCatching { search(keyword,sourcePage,type) }.getOrNull() ?: continue
                answered=true
                result.items.forEach { collected[it.id]=it }
                more=more || result.hasMore
            }
            if(!more) break
        }
        val aliases=runCatching { searchSuggestSeries(keyword,20) }.getOrDefault(emptyList())
        if(aliases.isNotEmpty()) answered=true
        aliases.forEach { collected[it.id]=it }
        // 搜索接口只回当前季，兄弟季要靠「建议接口 + 前两页 + 逐季补搜」凑齐；只在查询像剧名且结果很少时补，避免拖慢普通搜索
        val base=Seasons.base(keyword)
        val titleLike=base.length>=4 && base.any { it.code>0x7f }
        val family=if(titleLike && collected.size<=5) runCatching { searchSeasonFamily(base,Seasons.currentSeason(base,keyword),collected.values) }.getOrDefault(emptyList()) else emptyList()
        // 和「查找其他季」同一套排序：先季号、再标题
        val items=Seasons.order(base,(collected.values+family).distinctBy { it.id })
        // 一次都没请求成功才算搜索不可用；接口正常但没有结果时返回空列表，让界面显示「没有找到相关结果」
        if(items.isEmpty() && !answered) throw IOException("Search unavailable")
        return CatalogPage(items,false)
    }
    fun searchSuggest(keyword: String,count: Int=10): List<String> {
        val query=keyword.trim()
        if(query.isEmpty()) return emptyList()
        val limit=count.coerceIn(1,30)
        val webId=Signer.encode(VendorConstants.device.getValue("device_id"))
        val url="$site/incent_resource/suggestion?app_id=8662&web_id=$webId&query=${Signer.encode(query)}&count=$limit"
        val data=JSONObject(text(url))
        val arr=data.optJSONArray("suggest_list") ?: data.optJSONObject("data")?.optJSONArray("suggest_list") ?: return emptyList()
        return arr.objects().mapNotNull { it.str("name").ifBlank { null } }.take(limit)
    }
    /** 搜索建议里带完整剧集信息（季名、集数、简介），是「其他季」最全的来源，也能补上 App/网页搜索漏掉的季。 */
    fun searchSuggestSeries(keyword: String,count: Int=20): List<Series> {
        val query=keyword.trim()
        if(query.isEmpty()) return emptyList()
        val limit=count.coerceIn(1,30)
        val webId=Signer.encode(VendorConstants.device.getValue("device_id"))
        val url="$site/incent_resource/suggestion?app_id=8662&web_id=$webId&query=${Signer.encode(query)}&count=$limit"
        val data=JSONObject(text(url))
        val arr=data.optJSONArray("suggest_list") ?: data.optJSONObject("data")?.optJSONArray("suggest_list") ?: return emptyList()
        val rows=arr.objects().mapNotNull { row ->
            val video=row.optJSONObject("video_data")
            // 只收正式剧名条目：纠错联想/聚合词（word_type=title、common_query）不是剧集，标题里却常含查询词
            val kind=row.str("word_type")
            if(kind.isNotEmpty() && kind!="short_play_name") return@mapNotNull null
            val id=row.str("keyword").ifEmpty { video?.str("series_id").orEmpty() }
            val title=row.str("name").ifEmpty { video?.str("title").orEmpty() }
            if(id.isBlank() || title.isBlank()) return@mapNotNull null
            val episodes=video?.optInt("episode_cnt") ?: 0
            val tags=video?.optJSONArray("category_list") ?: JSONArray()
            Series(id,title,cleanUrl(video?.str("series_cover").orEmpty()),video?.str("series_intro").orEmpty(),
                video?.str("episode_right_text").orEmpty().ifEmpty { if(episodes>0) "全 $episodes 集" else "" },
                (0 until minOf(tags.length(),5)).joinToString(" · ") { tags.optJSONObject(it)?.str("name") ?: tags.optString(it) })
        }
        return valid(rows).filter { Seasons.matchesQuery(query,it.title) }
    }
    /**
     * 同名剧的全部季：建议接口 + App/网页搜索前两页 + 按缺失季号逐季补搜。
     * 搜索页与「查找其他季」共用这一套，保证两边列出来的季一致；
     * skipSeason 是正在看/正在搜的那一季（不用再探测），budget 是补搜请求上限，known 是调用方已经拿到的候选。
     */
    fun searchSeasonFamily(baseTitle: String, skipSeason: Int, known: Collection<Series> = emptyList(), budget: Int = 12): List<Series> {
        val base=Seasons.base(baseTitle).trim()
        val normBase=Seasons.norm(base)
        if(base.isEmpty() || normBase.isEmpty()) return known.toList()
        val rows=LinkedHashMap<String,Series>()
        known.forEach { rows[it.id]=it }
        runCatching { searchSuggestSeries(base,20) }.getOrDefault(emptyList()).forEach { rows[it.id]=it }
        for(type in listOf(ContentType.COMIC,ContentType.SHORT)) {
            var page=1
            while(page<=2) {
                val result=runCatching { search(base,page,type) }.getOrNull() ?: break
                result.items.forEach { rows[it.id]=it }
                if(!result.hasMore) break
                page++
            }
        }
        if(budget>0) {
            val arabic=rows.values.any { Regex("第[0-9]").containsMatchIn(it.title) }
            val highest=rows.values.mapNotNull { Seasons.number(it.title).takeIf { n -> n>0 } }.maxOrNull() ?: 0
            val upper=(maxOf(highest,skipSeason,6)+4).coerceAtMost(40)
            var probes=budget
            for(season in 1..upper) {
                if(probes<=0) break
                if(season==skipSeason || rows.values.any { Seasons.number(it.title)==season }) continue
                for(spelling in if(arabic) listOf(season.toString(),Seasons.numeral(season)) else listOf(Seasons.numeral(season),season.toString())) {
                    if(probes<=0 || rows.values.any { Seasons.number(it.title)==season }) break
                    probes--
                    val query=if(season==1) base else "$base 第${spelling}季"
                    runCatching { searchSuggestSeries(query,8) }.getOrDefault(emptyList()).forEach { rows[it.id]=it }
                }
            }
        }
        return rows.values.filter { Seasons.norm(it.title).contains(normBase) }
    }
    private fun isInitialsQuery(value: String) = value.trim().matches(Regex("[A-Za-z0-9]{2,20}"))
    fun searchSuggestRaw(keyword: String): String {
        val values=VendorConstants.device.toMutableMap()
        values.putAll(linkedMapOf("query" to keyword,"image_scale" to "1","aid" to "1967","channel" to "0","widget" to "2"))
        val signed=signer.sign("https://api5-normal-sinfonlinea.fqnovel.com/reading/bookapi/search/suggest/v/",values)
        return text(signed.url,signed).take(500)
    }
    fun detail(id: String): Detail {
        require(id.matches(Regex("[0-9]{1,30}")))
        val o=router("$site/detail?series_id=$id").optJSONObject("detail_page")?.optJSONObject("seriesDetail") ?: throw IOException("剧集详情暂不可用")
        val raw=o.optJSONArray("vid_list") ?: throw IOException("分集暂不可用")
        val episodes=(0 until raw.length()).map { raw.getString(it) }
        if(episodes.isEmpty() || episodes.any { !it.matches(Regex("[0-9]{1,30}")) } || episodes.distinct().size!=episodes.size) throw IOException("分集数据异常")
        return Detail(card(o).copy(id=id),episodes)
    }
    fun stream(id: String, maxQuality: Int = 1080, allowHevc: Boolean = true): StreamInfo {
        require(id.matches(Regex("[0-9]{1,30}")))
        val biz="""{"detail_page_version":0,"device_level":3,"disable_digg_stat":false,"need_all_video_definition":true,"need_mp4_align":false,"use_os_player":false,"use_server_dns":false,"video_platform":1024}"""
        val legacyUrl="https://api5-normal-sinfonlineb.fqnovel.com/novel/player/multi_video_model/v1/"
        val preloadUrl="https://api5-normal-sinfonlineb.fqnovel.com/novel/player/multi_video_model/preload/v1"
        val legacyBody="""{"biz_param":$biz,"mixed_video_id_map":{"1004":["$id"]}}""".toByteArray()
        val preloadBody="""{"biz_param":$biz,"dr_scene":"preload","mixed_video_id_map":{"1004":["$id"]}}""".toByteArray()
        fun attempt(device: Map<String,String>): StreamInfo? =
            streamFromModel(id,modelFrom(legacyUrl,legacyBody,id,device),maxQuality,allowHevc)
                ?: streamFromModel(id,modelFrom(preloadUrl,preloadBody,id,device),maxQuality,allowHevc)
        // Keep the stable device identity first (the identity that played without artifacts); only fall back
        // to a fresh synthetic identity when the fixed one yields nothing (e.g. temporary throttling).
        attempt(VendorConstants.device)?.let { return it }
        attempt(freshDevice())?.let { return it }
        // Website public MP4 (plain H.264, low resolution): also used when the play API is unavailable.
        val publicUrl="https://hongguoduanju.com/video/public/$id/360p.mp4"
        val ok=runCatching { RequestScope.execute(http.newCall(Request.Builder().url(publicUrl).header("Range","bytes=0-1").header("User-Agent",VendorConstants.VIDEO_UA).build())) { it.isSuccessful } }.getOrDefault(false)
        if(ok) {
            val result=StreamInfo(publicUrl,null,"360P")
            result.danmakuRaw=JSONArray(); result.episodeId=id; result.codec="h264"
            return result
        }
        throw IOException("暂时无法获取播放地址，请稍后重试")
    }
    private fun modelFrom(endpoint: String, body: ByteArray, id: String, device: Map<String,String>): JSONObject? = runCatching {
        val signed=signer.sign(endpoint,device,body)
        val data=JSONObject(text(signed.url,signed))
        if(data.optInt("Code",data.optInt("code",0))!=0) return@runCatching null
        val map=data.optJSONObject("data") ?: return@runCatching null
        val entry=map.optJSONObject(id) ?: map.keys().asSequence().mapNotNull { map.optJSONObject(it) }.firstOrNull() ?: return@runCatching null
        entry.optJSONObject("video_model") ?: JSONObject(entry.str("video_model"))
    }.getOrNull()
    private fun codecRank(codec: String)=when(codec.lowercase()) { "h264","avc","avc1" -> 0; "bytevc2","bvc2" -> 3; "bytevc1","hvc1","hev1","hevc","h265" -> 2; else -> 1 }
    private fun <T> pickBest(rows: List<Triple<Int,T,JSONObject>>, maxQuality: Int, allowHevc: Boolean): Triple<Int,T,JSONObject>? {
        val allowed=rows.filter { codecRank(it.third.str("codec_type"))!=3 }
        val preferred=allowed.filter { allowHevc || codecRank(it.third.str("codec_type"))<2 }
        val pool=preferred.ifEmpty { allowed }
        if(allowed.isEmpty()) return null
        // Compatibility first: prefer H.264/unknown over HEVC, then the highest quality within that codec.
        val topRank=pool.minOf { codecRank(it.third.str("codec_type")) }
        val group=pool.filter { codecRank(it.third.str("codec_type"))==topRank }.sortedByDescending { it.first }
        return group.firstOrNull { it.first<=maxQuality } ?: group.last()
    }
    private fun qualityLabel(q: Int, maxQuality: Int)=if(q>0) "${q}P"+(if(q>maxQuality) "（兼容资源）" else "") else "自动"
    private fun codecNote(codec: String, allowHevc: Boolean)=if(!allowHevc && codecRank(codec)==2) "（HEVC 软解）" else ""
    private fun streamFromModel(id: String, model: JSONObject?, maxQuality: Int, allowHevc: Boolean): StreamInfo? {
        model ?: return null
        val durationMs=(model.optDouble("video_duration",0.0)*1000).toLong()
        val list=model.optJSONArray("video_list")
        if(list!=null && list.length()>0) {
            val rows=list.objects().mapNotNull { o ->
                val meta=o.optJSONObject("video_meta") ?: o
                if(o.str("main_url").isEmpty()) null
                else Triple(qualityNumber(meta.str("definition").ifEmpty { meta.str("vheight") }),o,meta)
            }
            pickBest(rows,maxQuality,allowHevc)?.let { chosen ->
                val item=chosen.second; val url=cleanUrl(item.str("main_url"))
                if(url.startsWith("https://") || url.startsWith("http://")) {
                    val key=item.optJSONObject("encrypt_info")?.str("spade_a")?.takeIf { it.isNotEmpty() }?.let(MediaCrypto::deriveKey)
                    val codec=chosen.third.str("codec_type")
                    val result=StreamInfo(url,key,qualityLabel(chosen.first,maxQuality)+codecNote(codec,allowHevc))
                    result.debugRaw=model.toString(); result.danmakuRaw=JSONArray(); result.episodeId=id; result.durationMs=durationMs
                    result.codec=codec
                    return result
                }
            }
        }
        val raw=model.opt("fallback_api")
        val fallback=when(raw) { is JSONObject -> raw.str("fallback_api"); is JSONArray -> raw.optString(0); else -> runCatching { JSONObject(raw.toString()).str("fallback_api") }.getOrDefault(raw.toString()) }
        if(fallback.startsWith("https://")) {
            val info=runCatching { JSONObject(text(fallback)).optJSONObject("video_info")?.optJSONObject("data") }.getOrNull()
            val videoList=info?.optJSONObject("video_list")
            if(videoList!=null) {
                val seed=info.str("key_seed")
                val rows=videoList.keys().asSequence().mapNotNull { k -> videoList.optJSONObject(k)?.takeIf { it.str("main_url").isNotEmpty() }?.let { o -> Triple(qualityNumber(o.str("quality_desc").ifEmpty { o.str("height").ifEmpty { o.str("vheight").ifEmpty { o.str("quality").ifEmpty { k } } } }),k,o) } }.toList()
                pickBest(rows,maxQuality,allowHevc)?.let { selected ->
                    val item=selected.third; var url=cleanUrl(item.str("main_url"))
                    if(seed.isNotEmpty()) url=MediaCrypto.decryptUrl(url,decode64(seed))
                    if(url.startsWith("https://") || url.startsWith("http://")) {
                        val key=item.str("spade_a").takeIf { it.isNotEmpty() }?.let(MediaCrypto::deriveKey)
                        val codec=item.str("codec_type")
                        val result=StreamInfo(url,key,qualityLabel(selected.first,maxQuality)+codecNote(codec,allowHevc))
                        result.debugRaw=info.toString(); result.danmakuRaw=JSONArray(); result.episodeId=id
                        result.durationMs=(info.optDouble("video_duration",0.0)*1000).toLong()
                        result.codec=codec
                        return result
                    }
                }
            }
        }
        return null
    }
    fun fetchDanmakuWindow(episodeId: String, offset: Long, durationMs: Long): JSONArray {
        val result=JSONArray()
        try {
            var cursor=""
            var pageStop=false
            var pages=0
            while(!pageStop && pages<5) {
                pages++
                val bodyObj=JSONObject().apply {
                    put("comment_source",601); put("server_channel",1000); put("group_id",episodeId); put("group_type",30)
                    put("comment_type",20); put("sort",0)
                    put("business_param",JSONObject().apply {
                        put("book_id",episodeId); put("start_offset_time",offset); put("playlet_item_duration",durationMs)
                        put("need_danmaku_guide_type",org.json.JSONArray().apply { put(1);put(3);put(4);put(2) })
                    })
                    put("count",90); put("cursor",cursor); put("aid",8662); put("compliance_status",0)
                }
                val bodyBytes=bodyObj.toString().toByteArray()
                val signed=signer.sign("https://api5-normal-sinfonlineb.fqnovel.com/novel/commentapi/comment/list/${java.net.URLEncoder.encode(episodeId,"UTF-8")}/v1/",VendorConstants.device,bodyBytes)
                val extraHeaders=signed.headers + mapOf("comment-source" to "601","server-channel" to "1000","x-ss-stub" to "")
                val request=Request.Builder().url(signed.url).header("User-Agent",VendorConstants.VIDEO_UA).header("Accept-Language","zh-CN,zh;q=0.9")
                extraHeaders.forEach { (k,v) -> request.header(k,v) }
                request.post(bodyBytes.toRequestBody("application/json; charset=utf-8".toMediaType()))
                val resp=RequestScope.execute(http.newCall(request.build())) { it.body?.string() ?: "empty" }
                val respJson=JSONObject(resp)
                val dataList=respJson.optJSONObject("data")?.optJSONArray("data_list")
                if(dataList!=null) for(i in 0 until dataList.length()) result.put(dataList.getJSONObject(i))
                val hasMore=respJson.optJSONObject("data")?.optBoolean("has_more",false) ?: false
                cursor=respJson.optJSONObject("data")?.optString("cursor","") ?: ""
                if(!hasMore || cursor.isEmpty()) pageStop=true
            }
        } catch(_: Exception) {}
        return result
    }
    companion object {
        fun extractRouter(html: String): JSONObject {
            val marker=html.indexOf("_ROUTER_DATA"); if(marker<0) throw IOException("页面没有内容数据")
            val first=html.indexOf('{',marker); if(first<0) throw IOException("页面数据格式错误")
            var depth=0; var quoted=false; var escaped=false
            for(i in first until html.length) {
                val ch=html[i]
                if(quoted) { if(escaped) escaped=false else if(ch=='\\') escaped=true else if(ch=='"') quoted=false; continue }
                when(ch) { '"' -> quoted=true; '{','[' -> depth++; '}',']' -> { depth--; if(depth==0) return JSONObject(html.substring(first,i+1)) } }
            }
            throw IOException("页面数据不完整")
        }
        fun qualityNumber(value: String): Int = Regex("2160|1440|1080|720|576|540|480|360").find(value)?.value?.toInt() ?: mapOf("1920" to 1080,"1280" to 720,"1024" to 576,"854" to 480,"640" to 360)[value] ?: value.toIntOrNull() ?: 0
    }
}
