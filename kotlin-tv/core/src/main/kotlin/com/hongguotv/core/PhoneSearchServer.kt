// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.Locale

/** A persistent LAN form for sending search titles to the TV. */
class PhoneSearchServer(
    address: String,
    private val onQuery: (String)->Unit,
    private val onClear: ()->Unit = {},
    private val onExpired: ()->Unit = {},
    private val port: Int = 8787
): Closeable {
    private val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
    private val localAddress=InetAddress.getByName(address).also { require(it.isLoopbackAddress || it.isSiteLocalAddress) }.hostAddress
    private val listener=ServerSocket().apply { reuseAddress=true; bind(java.net.InetSocketAddress(port),4); soTimeout=500 }
    private val host="$localAddress:${listener.localPort}"
    val url="http://$host/"
    private val thread=Thread({ serve() },"hongguotv-phone-input").apply { isDaemon=true }
    init {
        thread.start()
    }
    override fun close() { stopped.set(true); runCatching { listener.close() } }
    private fun serve() {
        try {
            while(!stopped.get()) {
                val client=try { listener.accept() } catch(_: SocketTimeoutException) { continue }
                client.use { socket ->
                    socket.soTimeout=2000
                    try { handle(socket) } catch(_: Exception) { /* Discard malformed or disconnected requests. */ }
                }
            }
        } catch(_: Exception) { /* Closing the dialog interrupts accept(). */ }
        finally {
            close()
            if(!stopped.get()) onExpired()
        }
    }
    private fun line(input: InputStream, max: Int): String {
        val out=ByteArrayOutputStream()
        while(out.size()<max) {
            val next=input.read()
            if(next<0) throw java.io.EOFException()
            if(next==10) return out.toString("US-ASCII").trimEnd('\r')
            out.write(next)
        }
        throw java.io.IOException("Header too large")
    }
    private fun handle(socket: Socket) {
        if(!socket.inetAddress.let { it.isLoopbackAddress || it.isSiteLocalAddress }) return
        val input=socket.getInputStream().buffered()
        val request=line(input,1024).split(' ')
        if(request.size!=3) return
        val headers=mutableMapOf<String,String>()
        var bytes=0
        while(true) {
            val next=line(input,4096); bytes+=next.length+2
            if(bytes>8192) return respond(socket,431,"请求过大")
            if(next.isEmpty()) break
            val at=next.indexOf(':'); if(at<=0) return respond(socket,400,"请求格式错误")
            val key=next.substring(0,at).lowercase(Locale.ROOT)
            if(headers.put(key,next.substring(at+1).trim())!=null) return respond(socket,400,"重复请求头")
        }
        if(stopped.get()) return respond(socket,410,"手机推送已关闭")
        val path=request[1].substringBefore('?')
        val queryString=request[1].substringAfter('?', "")
        if(headers["host"]!=host || path !in listOf("/","/submit","/clear")) return respond(socket,404,"页面不存在")
        if(request[0]=="GET" && path=="/") return respond(socket,200,page(null))
        if(request[0]=="GET" && path=="/clear") { onClear(); return respond(socket,200,page("已清空电视输入框。")) }
        if(request[0]=="GET" && path=="/submit") {
            val query=parseQuery(queryString) ?: return respond(socket,400,"请输入 1—80 个字符的剧名")
            respond(socket,200,page("已发送到电视，可以继续发送下一个剧名。"))
            onQuery(query); return
        }
        if(request[0]!="POST") return respond(socket,405,"不支持的操作")
        val origin=headers["origin"]
        if(origin!=null && origin!="null" && origin.trimEnd('/')!="http://$host") return respond(socket,403,"来源不匹配")
        if(headers.containsKey("transfer-encoding") || headers["content-type"]?.substringBefore(';')!="application/x-www-form-urlencoded") return respond(socket,400,"请求格式错误")
        val length=headers["content-length"]?.toIntOrNull()?.takeIf { it in 0..4096 } ?: return respond(socket,413,"输入内容过长")
        val body=ByteArray(length); var offset=0
        while(offset<body.size) { val n=input.read(body,offset,body.size-offset); if(n<0) return; offset+=n }
        if(path=="/clear") { onClear(); return respond(socket,200,page("已清空电视输入框。")) }
        val query=parseQuery(String(body,Charsets.UTF_8)) ?: return respond(socket,400,"请输入 1—80 个字符的剧名")
        respond(socket,200,page("已发送到电视，可以继续发送下一个剧名。"))
        onQuery(query)
    }
    private fun parseQuery(text: String): String? {
        val fields=text.split('&').map { it.split('=',limit=2) }
        if(fields.count { it.size==2 && it[0]=="query" }!=1) return null
        val value=runCatching { URLDecoder.decode(fields.first { it.size==2 && it[0]=="query" }[1],"UTF-8").trim() }.getOrNull() ?: return null
        if(value.isBlank() || value.length>80 || value.any { it.isISOControl() }) return null
        return value
    }
    private fun page(status: String?): String {
        val notice=if(status==null) "" else "<p class=\"status\">$status</p>"
        return """<h1>发送剧名到电视</h1><p>输入剧名，返回电视后选择搜索。</p><form method="post" action="/submit"><input name="query" maxlength="80" required autofocus placeholder="输入剧名或关键词" autocomplete="off"><div class="actions"><a class="clear" href="/clear">清空电视输入框</a><button type="submit" class="send">发送到电视</button></div></form>$notice"""
    }
    private fun respond(socket: Socket, code: Int, message: String) {
        val body="""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>红果 TV · 手机推送</title><style>body{font:18px sans-serif;background:#10131b;color:#f4f5f8;max-width:500px;margin:40px auto;padding:20px}input,button{box-sizing:border-box;width:100%;font:inherit;padding:16px;margin:10px 0;border-radius:8px}button{color:white;border:0;background:#ff634c}p{line-height:1.6}.actions{display:flex;gap:10px}.actions>*{flex:1;width:auto}.send{background:#ff634c}.clear{display:block;box-sizing:border-box;text-align:center;text-decoration:none;color:white;padding:16px;margin:10px 0;border-radius:8px;background:#3a4658}.status{background:#182033;border-radius:8px;padding:12px;margin-top:16px}</style></head><body>$message</body></html>""".toByteArray(Charsets.UTF_8)
        val header="HTTP/1.1 $code Response\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: same-origin\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }
}
