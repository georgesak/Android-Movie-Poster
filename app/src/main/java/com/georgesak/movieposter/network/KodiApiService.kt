package com.georgesak.movieposter.network

import com.georgesak.movieposter.data.KodiActivePlayer
import com.georgesak.movieposter.data.KodiItemResponse
import com.georgesak.movieposter.data.KodiPlayerProperties
import com.georgesak.movieposter.data.KodiRpcRequest
import com.georgesak.movieposter.data.KodiRpcResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface KodiApiService {
    @POST("jsonrpc")
    suspend fun getActivePlayers(@Body request: KodiRpcRequest): Response<KodiRpcResponse<List<KodiActivePlayer>>>

    @POST("jsonrpc")
    suspend fun getPlayerProperties(@Body request: KodiRpcRequest): Response<KodiRpcResponse<KodiPlayerProperties>>

    @POST("jsonrpc")
    suspend fun getPlayerItem(@Body request: KodiRpcRequest): Response<KodiRpcResponse<KodiItemResponse>>
}