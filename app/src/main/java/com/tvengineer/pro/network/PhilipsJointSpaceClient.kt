package com.tvengineer.pro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PhilipsJointSpaceClient(private val host:String, private val port:Int=1925):TvClient {
    private val client=OkHttpClient()
    private val json="application/json".toMediaType()

    override suspend fun send(payload:String)= withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()){"Set the Philips TV IP first"}
            val body=JSONObject().put("key",payload).toString().toRequestBody(json)
            val req=Request.Builder().url("http://$host:$port/6/input/key").post(body).build()
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful) error("Philips HTTP ${r.code}")
                "Philips $payload"
            }
        }
    }
    override suspend fun probe()= withContext(Dispatchers.IO) {
        runCatching {
            val req=Request.Builder().url("http://$host:$port/6/system").get().build()
            client.newCall(req).execute().use { r ->
                if(!r.isSuccessful) error("Philips HTTP ${r.code}")
                r.body?.string()?.take(500) ?: "Philips online"
            }
        }
    }
}
