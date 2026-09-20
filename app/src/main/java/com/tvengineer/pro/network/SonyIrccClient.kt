package com.tvengineer.pro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SonyIrccClient(private val host:String, private val psk:String):TvClient {
    private val client=OkHttpClient()
    override suspend fun send(payload:String)= withContext(Dispatchers.IO) {
        runCatching {
            require(host.isNotBlank()){"Set the Sony TV IP first"}
            require(payload.isNotBlank()){"Sony IRCC command code is empty"}
            val xml="""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$payload</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
            val b=xml.toRequestBody("text/xml; charset=UTF-8".toMediaType())
            val rb=Request.Builder().url("http://$host/sony/IRCC")
                .addHeader("SOAPACTION","\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            if(psk.isNotBlank()) rb.addHeader("X-Auth-PSK",psk)
            client.newCall(rb.post(b).build()).execute().use { r ->
                if(!r.isSuccessful) error("Sony HTTP ${r.code}")
                "Sony IRCC sent"
            }
        }
    }

    override suspend fun probe()= withContext(Dispatchers.IO) {
        runCatching {
            val rb=Request.Builder().url("http://$host/sony/system")
            if(psk.isNotBlank()) rb.addHeader("X-Auth-PSK",psk)
            val b="""{"method":"getSystemInformation","params":[],"id":1,"version":"1.0"}"""
                .toRequestBody("application/json".toMediaType())
            client.newCall(rb.post(b).build()).execute().use { r ->
                if(!r.isSuccessful) error("Sony HTTP ${r.code}")
                r.body?.string()?.take(500) ?: "Sony online"
            }
        }
    }
}
