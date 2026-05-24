package com.basauri.ftmowidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

    suspend fun fetchMetrix(login: Long, sharingCode: String): MetrixResponse {
        val raw = getRaw("$baseUrl/metrix/$login/$sharingCode")
        return decode(raw, MetrixResponse.serializer(), listOf("metrixData", "data", "result", "payload"))
    }

    suspend fun fetchBalanceCurve(login: Long, sharingCode: String): BalanceCurveResponse {
        val raw = getRaw("$baseUrl/account/$login/$sharingCode/balance-curve")
        return decode(raw, BalanceCurveResponse.serializer(), listOf("balanceCurveData", "data", "result", "payload"))
    }

    private suspend fun getRaw(url: String): String = withContext(Dispatchers.IO) {
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
                throw IOException("HTTP ${response.code} from $url · ${body.take(200)}")
            }
            body
        }
    }

    /**
     * Tries to decode [body] as [T] directly; if the root JSON object is a wrapper, looks for the
     * actual payload under any of [wrapperKeys] before retrying. On hard failure, raises an
     * IOException whose message includes the first 280 chars of the body so the UI can show the
     * actual shape and unblock further iteration.
     */
    private fun <T> decode(
        body: String,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        wrapperKeys: List<String>,
    ): T {
        val attempts = mutableListOf<Throwable>()
        val root: JsonElement = try {
            json.parseToJsonElement(body)
        } catch (t: Throwable) {
            throw IOException("Invalid JSON · ${body.take(280)}", t)
        }

        val candidates = buildList<JsonElement> {
            add(root)
            (root as? JsonObject)?.let { obj ->
                for (key in wrapperKeys) obj[key]?.let { add(it) }
                // Last resort: if the root has a single object property, unwrap it.
                if (obj.size == 1) obj.values.firstOrNull()?.let { add(it) }
            }
        }.distinct()

        for (candidate in candidates) {
            try {
                return json.decodeFromJsonElement(deserializer, candidate)
            } catch (t: Throwable) {
                attempts += t
            }
        }
        val first = attempts.firstOrNull()?.message ?: "unknown parse error"
        throw IOException("Could not decode response · $first · body: ${body.take(280)}")
    }
}
