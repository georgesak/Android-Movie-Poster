package com.georgesak.movieposter.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object KodiApiClient {
    fun createKodiApiService(ipAddress: String, port: Int): KodiApiService {
        val httpClient = OkHttpClient.Builder().build()

        val baseUrl = "http://$ipAddress:$port/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(KodiApiService::class.java)
    }
}