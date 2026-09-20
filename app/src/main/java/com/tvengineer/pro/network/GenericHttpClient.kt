package com.tvengineer.pro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GenericHttpClient(private val host:String):TvClient {
    private val client=OkHttpClient()
    override suspend fun send(payload:String)= withContext(Dispatchers.IO) {
        runCatching {
            require(payload.startsWith("http://") || payload.startsWith("https://")) {
                "Generic HTTP payload must be a full URL"
            }
            client.newCall(Request.Builder().url(payload.replace("{host}",host)).get().build()).execute().use { r ->
                if(!r.isSuccessful) error("HTTP ${r.code}")
                "HTTP command sent"
            }
        }
    }
    override suspend fun probe()= withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()){"Set host first"}
            client.newCall(Request.Builder().url("http://$host").get().build()).execute().use { "HTTP ${it.code}" }
        }
    }
}
