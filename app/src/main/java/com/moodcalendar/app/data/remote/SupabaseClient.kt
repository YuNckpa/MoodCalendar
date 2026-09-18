package com.moodcalendar.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient {
    val jsonParser: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean get() = SupabaseConfig.isConfigured

    suspend fun signUp(email: String, password: String, displayName: String): AuthTokenResponse {
        val raw = request(
            method = "POST",
            path = "/auth/v1/signup",
            accessToken = null,
            body = jsonParser.encodeToString(
                buildJsonObject {
                    put("email", email)
                    put("password", password)
                    put("data", buildJsonObject { put("display_name", displayName) })
                    put("email_redirect_to", SupabaseConfig.AUTH_REDIRECT)
                }
            ).toRequestBody(jsonMedia)
        )
        return parseAuthResponse(raw)
    }

    suspend fun resendSignupEmail(email: String) {
        request(
            method = "POST",
            path = "/auth/v1/resend",
            accessToken = null,
            body = jsonParser.encodeToString(
                buildJsonObject {
                    put("type", "signup")
                    put("email", email)
                    put("email_redirect_to", SupabaseConfig.AUTH_REDIRECT)
                }
            ).toRequestBody(jsonMedia)
        )
    }

    suspend fun verifyOtp(email: String, token: String, type: String): AuthTokenResponse =
        authPost(
            path = "/auth/v1/verify",
            body = buildJsonObject {
                put("email", email)
                put("token", token)
                put("type", type)
            }
        )

    suspend fun recoverPassword(email: String) {
        request(
            method = "POST",
            path = "/auth/v1/recover",
            accessToken = null,
            body = jsonParser.encodeToString(
                buildJsonObject {
                    put("email", email)
                    put("email_redirect_to", SupabaseConfig.AUTH_REDIRECT)
                }
            ).toRequestBody(jsonMedia)
        )
    }

    suspend fun updatePassword(accessToken: String, newPassword: String) {
        request(
            method = "PUT",
            path = "/auth/v1/user",
            accessToken = accessToken,
            body = jsonParser.encodeToString(
                buildJsonObject { put("password", newPassword) }
            ).toRequestBody(jsonMedia)
        )
    }

    suspend fun signIn(email: String, password: String): AuthTokenResponse =
        authPost(
            path = "/auth/v1/token?grant_type=password",
            body = buildJsonObject {
                put("email", email)
                put("password", password)
            }
        )

    suspend fun refresh(refreshToken: String): AuthTokenResponse =
        authPost(
            path = "/auth/v1/token?grant_type=refresh_token",
            body = buildJsonObject { put("refresh_token", refreshToken) }
        )

    suspend fun signOut(accessToken: String) {
        request(
            method = "POST",
            path = "/auth/v1/logout",
            accessToken = accessToken,
            body = "{}".toRequestBody(jsonMedia)
        )
    }

    suspend fun getRaw(
        table: String,
        accessToken: String,
        query: String = "",
        single: Boolean = false
    ): String {
        val path = "/rest/v1/$table${if (query.isBlank()) "" else "?$query"}"
        val headers = mutableMapOf<String, String>()
        if (single) headers["Accept"] = "application/vnd.pgrst.object+json"
        return request("GET", path, accessToken, extraHeaders = headers)
    }

    suspend fun postRaw(
        table: String,
        accessToken: String,
        bodyJson: String,
        onConflict: String? = null,
        upsert: Boolean = false
    ): String {
        val path = buildString {
            append("/rest/v1/$table")
            if (!onConflict.isNullOrBlank()) append("?on_conflict=$onConflict")
        }
        val prefer = if (upsert) {
            "resolution=merge-duplicates,return=representation"
        } else {
            "return=representation"
        }
        return request(
            method = "POST",
            path = path,
            accessToken = accessToken,
            body = bodyJson.toRequestBody(jsonMedia),
            extraHeaders = mapOf("Prefer" to prefer)
        )
    }

    suspend inline fun <reified T> select(
        table: String,
        accessToken: String,
        query: String = "",
        single: Boolean = false
    ): T {
        val raw = getRaw(table, accessToken, query, single)
        return jsonParser.decodeFromString(raw.ifBlank { if (single) "{}" else "[]" })
    }

    suspend inline fun <reified T> upsert(
        table: String,
        accessToken: String,
        rows: List<T>,
        onConflict: String? = null
    ): List<T> {
        val raw = postRaw(
            table = table,
            accessToken = accessToken,
            bodyJson = jsonParser.encodeToString(rows),
            onConflict = onConflict,
            upsert = true
        )
        return jsonParser.decodeFromString(raw.ifBlank { "[]" })
    }

    suspend inline fun <reified T> insert(
        table: String,
        accessToken: String,
        rows: List<T>
    ): List<T> {
        val raw = postRaw(
            table = table,
            accessToken = accessToken,
            bodyJson = jsonParser.encodeToString(rows),
            upsert = false
        )
        return jsonParser.decodeFromString(raw.ifBlank { "[]" })
    }

    suspend fun patch(
        table: String,
        accessToken: String,
        query: String,
        bodyJson: String
    ) {
        request(
            method = "PATCH",
            path = "/rest/v1/$table?$query",
            accessToken = accessToken,
            body = bodyJson.toRequestBody(jsonMedia),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }

    suspend fun delete(table: String, accessToken: String, query: String) {
        request(
            method = "DELETE",
            path = "/rest/v1/$table?$query",
            accessToken = accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }

    suspend fun uploadMoodImage(
        accessToken: String,
        userId: String,
        moodSyncId: String,
        file: File
    ): String {
        val objectPath = "$userId/$moodSyncId/${file.name}"
        return uploadPublicObject(accessToken, "mood-images", objectPath, file)
    }

    suspend fun uploadAvatar(accessToken: String, userId: String, file: File): String {
        // Unique path so profile URL changes and Coil/CDN caches refresh after replace.
        val objectPath = "$userId/avatar_${System.currentTimeMillis()}.jpg"
        return uploadPublicObject(accessToken, "avatars", objectPath, file)
    }

    fun publicUrl(objectPath: String): String =
        "${SupabaseConfig.url}/storage/v1/object/public/mood-images/$objectPath"

    fun avatarPublicUrl(objectPath: String): String =
        "${SupabaseConfig.url}/storage/v1/object/public/avatars/$objectPath"

    private suspend fun uploadPublicObject(
        accessToken: String,
        bucket: String,
        objectPath: String,
        file: File
    ): String {
        val url = "${SupabaseConfig.url}/storage/v1/object/$bucket/$objectPath"
        val req = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("x-upsert", "true")
            .post(file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException(parseError(body).ifBlank { "上传失败 (${resp.code})" })
                }
                "${SupabaseConfig.url}/storage/v1/object/public/$bucket/$objectPath"
            }
        }
    }

    private suspend fun authPost(path: String, body: JsonObject): AuthTokenResponse {
        val raw = request(
            method = "POST",
            path = path,
            accessToken = null,
            body = jsonParser.encodeToString(body).toRequestBody(jsonMedia)
        )
        return parseAuthResponse(raw)
    }

    /**
     * Signup with email confirmation often returns a bare user object (not a session).
     * Duplicate emails return the same shape with empty [AuthUserDto.identities].
     */
    fun parseAuthResponse(raw: String): AuthTokenResponse {
        if (raw.isBlank()) return AuthTokenResponse()
        val root = runCatching { jsonParser.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return runCatching {
                jsonParser.decodeFromString<AuthTokenResponse>(raw)
            }.getOrDefault(AuthTokenResponse())

        val hasSessionKeys = root.containsKey("access_token") || root.containsKey("refresh_token")
        val hasNestedUser = root.containsKey("user")
        if (hasSessionKeys || hasNestedUser) {
            val session = jsonParser.decodeFromJsonElement<AuthTokenResponse>(root)
            // Some payloads nest user; if missing but root looks like user fields, ignore.
            if (session.user != null || hasSessionKeys) return session
        }

        // Bare user object: { id, email, identities, ... }
        if (root.containsKey("id") && (root.containsKey("email") || root.containsKey("identities"))) {
            val user = jsonParser.decodeFromJsonElement<AuthUserDto>(root)
            return AuthTokenResponse(user = user)
        }

        return jsonParser.decodeFromJsonElement(root)
    }

    suspend fun request(
        method: String,
        path: String,
        accessToken: String?,
        body: RequestBody? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw IOException("未配置 Supabase：请在 local.properties 填写 supabase.url 与 supabase.anonKey")
        }
        val builder = Request.Builder()
            .url("${SupabaseConfig.url}$path")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Content-Type", "application/json")
        val bearer = accessToken ?: SupabaseConfig.anonKey
        builder.header("Authorization", "Bearer $bearer")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> builder.delete(body)
            else -> builder.method(method, body)
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(parseError(text).ifBlank { "请求失败 (${resp.code})" })
            }
            text
        }
    }

    fun parseError(raw: String): String {
        val err = runCatching { jsonParser.decodeFromString<AuthErrorBody>(raw) }.getOrNull()
        return listOfNotNull(err?.msg, err?.message, err?.errorDescription, err?.error)
            .firstOrNull { it.isNotBlank() }
            ?: raw.take(300)
    }
}
