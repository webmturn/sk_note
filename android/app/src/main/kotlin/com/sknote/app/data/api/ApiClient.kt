package com.sknote.app.data.api

import android.content.Context
import com.sknote.app.BuildConfig
import com.sknote.app.data.local.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var apiService: ApiService? = null
    private var tokenManager: TokenManager? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        tokenManager = TokenManager(context)
    }

    fun getService(): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildService().also { apiService = it }
        }
    }

    private fun buildService(): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val token = tokenManager?.cachedToken
            val request = if (!token.isNullOrEmpty()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            if (response.code == 401 && !token.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch { tokenManager?.clearAuth() }
            }
            response
        }

        appContext ?: throw IllegalStateException("ApiClient not initialized. Call init() first.")

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun getTokenManager(): TokenManager {
        return tokenManager ?: throw IllegalStateException("ApiClient not initialized. Call init() first.")
    }
}
