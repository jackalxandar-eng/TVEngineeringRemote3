package com.tvengineer.pro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LgWebOsClient(
    private val host:String,
    private val port:Int=3000,
    private val clientKey:String=""
): TvClient {
    private val client=OkHttpClient.Builder().readTimeout(8,TimeUnit.SECONDS).build()

    private val manifest=JSONObject()
        .put("manifestVersion",1)
        .put("appVersion","3.0")
        .put("signed",JSONObject()
            .put("created","20260920")
            .put("appId","com.tvengineer.pro")
            .put("vendorId","tvengineer")
            .put("localizedAppNames",JSONObject().put("","TV Engineer Pro"))
            .put("localizedVendorNames",JSONObject().put("","TV Engineer"))
            .put("permissions",JSONArray(listOf(
                "TEST_SECURE","CONTROL_INPUT_TEXT","CONTROL_MOUSE_AND_KEYBOARD",
                "READ_INSTALLED_APPS","READ_LGE_SDX","READ_NOTIFICATIONS","SEARCH",
                "WRITE_SETTINGS","WRITE_NOTIFICATION_ALERT","CONTROL_POWER","READ_CURRENT_CHANNEL",
                "READ_INPUT_DEVICE_LIST","READ_NETWORK_STATE","READ_TV_CHANNEL_LIST"
            ))))

    override suspend fun send(payload:String)= withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()){"Set the LG TV IP first"}
            val session=connectAndRegister()
            try {
                if(payload.startsWith("BUTTON:")) {
                    val name=payload.substringAfter(':')
                    sendPointerButton(session,name)
                } else {
                    sendSsap(session,payload)
                }
                "LG $payload"
            } finally { session.ws.close(1000,"done") }
        }
    }

    override suspend fun probe()= withContext(Dispatchers.IO) {
        runCatching {
            val s=connectAndRegister()
            s.ws.close(1000,"probe")
            "LG webOS paired/online"
        }
    }

    private data class Session(val ws:WebSocket)

    private fun connectAndRegister():Session {
        val latch=CountDownLatch(1)
        val failure=AtomicReference<Throwable?>()
        val req=Request.Builder().url("ws://$host:$port/").build()
        val holder=AtomicReference<WebSocket?>()
        val ws=client.newWebSocket(req,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){
                holder.set(webSocket)
                val payload=JSONObject().put("forcePairing",false).put("pairingType","PROMPT").put("manifest",manifest)
                if(clientKey.isNotBlank()) payload.put("client-key",clientKey)
                webSocket.send(JSONObject().put("type","register").put("id","register_0").put("payload",payload).toString())
            }
            override fun onMessage(webSocket:WebSocket,text:String){
                if(text.contains("\"registered\"") || text.contains("\"prompt\"")) latch.countDown()
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){failure.set(t);latch.countDown()}
        })
        if(!latch.await(6,TimeUnit.SECONDS)) { ws.cancel(); error("LG pairing timeout") }
        failure.get()?.let{throw it}
        return Session(ws)
    }

    private fun sendSsap(s:Session,uri:String) {
        val (realUri,payload)=if(uri.contains("?")) {
            val u=uri.substringBefore("?"); val q=uri.substringAfter("?")
            val obj=JSONObject()
            q.split("&").forEach { part ->
                val kv=part.split("=",limit=2)
                if(kv.size==2) obj.put(kv[0], kv[1].toBooleanStrictOrNull() ?: kv[1])
            }
            u to obj
        } else uri to JSONObject()
        s.ws.send(JSONObject().put("type","request").put("id","cmd_${System.nanoTime()}")
            .put("uri",realUri).put("payload",payload).toString())
        Thread.sleep(180)
    }

    private fun sendPointerButton(s:Session,name:String) {
        val latch=CountDownLatch(1)
        val socketPath=AtomicReference<String?>()
        val reqId="ptr_${System.nanoTime()}"
        // A second registration session is not necessary: get pointer socket on current session.
        // OkHttp listener is already internal, so request a commonly supported simple fallback first.
        val direct=mapOf(
            "HOME" to "ssap://system.launcher/open",
            "INPUT" to "ssap://tv/switchInput"
        )
        if(name in direct) { sendSsap(s,direct.getValue(name)); return }
        // Pointer buttons are model/version dependent. We preserve the request for profiles
        // that provide direct SSAP URIs and fail clearly rather than sending an unsafe command.
        error("Pointer key $name requires the webOS pointer-input socket. Import a model profile or use IR for navigation.")
    }
}
