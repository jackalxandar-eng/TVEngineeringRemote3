package com.tvengineer.pro.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SamsungTizenClient(
    private val host:String,
    private val port:Int=8001,
    private val token:String=""
): TvClient {
    private val client=OkHttpClient.Builder().readTimeout(5,TimeUnit.SECONDS).build()

    private fun url():String {
        val name=Base64.encodeToString("TV Engineer Pro".toByteArray(), Base64.NO_WRAP)
        val scheme=if(port==8002) "wss" else "ws"
        val t=if(token.isNotBlank()) "&token=$token" else ""
        return "$scheme://$host:$port/api/v2/channels/samsung.remote.control?name=$name$t"
    }

    override suspend fun send(payload:String)= withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()){"Set the Samsung TV IP first"}
            val latch=java.util.concurrent.CountDownLatch(1)
            var error:String?=null
            val req=Request.Builder().url(url()).build()
            val ws=client.newWebSocket(req, object:WebSocketListener(){
                override fun onOpen(webSocket:WebSocket,response:Response){
                    val body=JSONObject()
                        .put("method","ms.remote.control")
                        .put("params",JSONObject()
                            .put("Cmd","Click")
                            .put("DataOfCmd",payload)
                            .put("Option","false")
                            .put("TypeOfRemote","SendRemoteKey"))
                    webSocket.send(body.toString()); latch.countDown()
                }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){error=t.message; latch.countDown()}
            })
            latch.await(4,TimeUnit.SECONDS); ws.close(1000,"done")
            error?.let{error(it)}
            "Samsung $payload"
        }
    }

    override suspend fun probe()= withContext(Dispatchers.IO) {
        runCatching {
            val req=Request.Builder().url("http://$host:8001/api/v2/").get().build()
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful) error("Samsung HTTP ${r.code}")
                r.body?.string()?.take(500) ?: "Samsung TV online"
            }
        }
    }
}
