package com.example.mj_player_tv.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.util.concurrent.TimeUnit

object
RetrofitInstance {

    //Create the Retrofit service instance using the retrofit.
    fun getInstance(url:String): Api {
        Log.d("RetrofitRequest:", "THIS $url")
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // Verbindungs-Timeout
            .readTimeout(30, TimeUnit.SECONDS)    // Lese-Timeout
            .writeTimeout(30, TimeUnit.SECONDS)   // Schreib-Timeout
            .addInterceptor(loggingInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(SimpleXmlConverterFactory.create()) // Verwende den SimpleXmlConverterFactory
            .build()

        return retrofit.create(Api::class.java)
    }

    fun getXmlInstance(url: String): Api {
        Log.d("RetrofitRequest:", "THIS $url")

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true) // HTTP-Redirects verfolgen
            .build()

        val retrofit = Retrofit.Builder()
            .client(okHttpClient) // Verwende den angepassten OkHttpClient
            .baseUrl(url)
            .addConverterFactory(SimpleXmlConverterFactory.create()) // Verwende den SimpleXmlConverterFactory
            .build()

        return retrofit.create(Api::class.java)
    }

}