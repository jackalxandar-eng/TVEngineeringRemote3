package com.tvengineer.pro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class RokuClient(private val host:String, private val port:Int=8060): TvClient {
    private val client=OkHttpClient()

    override suspend fun send(payload:String)= withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()){"Set the Roku IP first"}
            val key=payload.trim()
            val req=Request.Builder()
                .url("http://$host:$port/keypress/$key")
                .post(FormBody.Builder().build()).build()
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful) error("Roku HTTP ${r.code}")
                "Roku keypress $key"
            }
        }
    }

    override suspend fun probe()= withContext(Dispatchers.IO) {
        runCatching {
            val req=Request.Builder().url("http://$host:$port/query/device-info").get().build()
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful) error("Roku HTTP ${r.code}")
                r.body?.string()?.take(500) ?: "Roku online"
            }
        }
    }
}
