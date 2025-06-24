package com.georgesak.movieposter.network

import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object KodiApiClient {
    fun createKodiApiService(ipAddress: String, port: Int, username: String, password: String): KodiApiService {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    val credential = Credentials.basic(username, password)
                    requestBuilder.header("Authorization", credential)
                }
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .build()

        val baseUrl = "http://$ipAddress:$port/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(KodiApiService::class.java)
    }
}