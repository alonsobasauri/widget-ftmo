package com.basauri.ftmowidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class FtmoClient(
    private val baseUrl: String = "https://gw2.ftmo.com/public-api/v1",
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun fetchMetrix(login: Long, sharingCode: String): MetrixResponse =
        get("$baseUrl/metrix/$login/$sharingCode")

    suspend fun fetchBalanceCurve(login: Long, sharingCode: String): BalanceCurveResponse =
        get("$baseUrl/account/$login/$sharingCode/balance-curve")

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "https://trader.ftmo.com/")
            .header("User-Agent", "FtmoWidget/0.1 (Android)")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url: ${body.take(200)}")
            }
            json.decodeFromString<T>(body)
        }
    }
}
