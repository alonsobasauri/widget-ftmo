package com.basauri.ftmowidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class BlueGuardianClient(
    private val baseUrl: String = "https://api.trader.blueguardian.com/v1",
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

    suspend fun fetchShared(sharedId: String): BgSharedResponse {
        val body = get("$baseUrl/accounts/shared/$sharedId")
        return decode(body) { json.decodeFromString(BgSharedResponse.serializer(), it) }
    }

    /**
     * Daily balance/equity series. This is a POST — a GET returns 404 — and the
     * JSON content type is load-bearing: without it the server ignores the body
     * and labels every point with the year instead of its date.
     */
    suspend fun fetchDailyGrowth(sharedId: String): List<BgGrowthPoint> {
        val body = post("$baseUrl/accounts/shared/$sharedId/growth", """{"filter":"daily"}""")
        return decode(body) { json.decodeFromString(ListSerializer(BgGrowthPoint.serializer()), it) }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).get(), url)
    }

    private suspend fun post(url: String, payload: String): String = withContext(Dispatchers.IO) {
        val body = payload.toRequestBody(JSON_MEDIA)
        execute(Request.Builder().url(url).post(body), url)
    }

    private fun execute(builder: Request.Builder, url: String): String {
        val request = builder
            .header("Accept", "application/json")
            .header("User-Agent", "FtmoWidget/0.1 (Android)")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url · ${body.take(200)}")
            }
            return body
        }
    }

    /** Surfaces the first 280 chars of an undecodable body so the UI can show the actual shape. */
    private fun <T> decode(body: String, block: (String) -> T): T = try {
        block(body)
    } catch (t: Throwable) {
        throw IOException("Could not decode Blue Guardian response · ${t.message} · body: ${body.take(280)}", t)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
