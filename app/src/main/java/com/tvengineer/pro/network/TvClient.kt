package com.tvengineer.pro.network

interface TvClient {
    suspend fun send(payload: String): Result<String>
    suspend fun probe(): Result<String>
}
