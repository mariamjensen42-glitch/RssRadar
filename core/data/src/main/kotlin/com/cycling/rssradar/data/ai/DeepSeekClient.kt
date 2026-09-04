package com.cycling.rssradar.core.data.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** AI 调用失败。[userMessage] 是给用户看的中文文案（issue #44：区分网络失败与 Key 无效）。 */
sealed class AiException(message: String, val userMessage: String) : Exception(message) {
    /** 未配置 API Key。 */
    class MissingKey : AiException("missing api key", "未配置 API Key，请到「我的」页设置")

    /** 网络/超时/连接失败。 */
    class Network(cause: Exception) : AiException("network: ${cause.message}", "网络失败，请检查网络后重试")

    /** DeepSeek 返回了错误码。401 单列——引导检查 Key。 */
    class Api(val code: Int, detail: String) : AiException(
        "api $code: $detail",
        if (code == 401) "API Key 无效，请到「我的」页检查" else "AI 服务报错（$code），请稍后重试",
    )

    /** 响应结构不符或内容为空。 */
    class EmptyResponse : AiException("empty response", "AI 没有返回内容，请重试")
}

/**
 * DeepSeek Chat 手写 client（issue #44，ADR-0005）：
 * OpenAI 兼容 `/chat/completions`，HttpURLConnection + kotlinx-serialization，零新依赖。
 * 模型固定 deepseek-chat（语言组织任务不需要 reasoner），readTimeout 60s（LLM 生成慢）。
 */
class DeepSeekClient(
    private val apiKeyProvider: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * [temperature] 为 null 时不发送该字段（走 DeepSeek 默认 1.0）。
     * 摘要等忠实性任务传低值（如 0.4）压发散，减少套话和注水。
     */
    suspend fun chat(system: String, user: String, temperature: Double? = null): String =
        withContext(ioDispatcher) {
            val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: throw AiException.MissingKey()
            val request = ChatRequest(
                model = MODEL,
                messages = listOf(
                    ChatRequest.Msg(role = "system", content = system),
                    ChatRequest.Msg(role = "user", content = user),
                ),
                temperature = temperature,
            )
        try {
            execute(key, request)
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.Network(e)
        }
    }

    private fun execute(key: String, request: ChatRequest): String {
        val connection = URL("$BASE_URL/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { out ->
                out.write(json.encodeToString(request).toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) throw AiException.Api(code, body?.take(300) ?: "")
            return AiText.parseChatCompletion(body ?: "") ?: throw AiException.EmptyResponse()
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val stream: Boolean = false,
        val temperature: Double? = null,
    ) {
        @Serializable
        data class Msg(val role: String, val content: String)
    }

    companion object {
        const val BASE_URL = "https://api.deepseek.com"
        const val MODEL = "deepseek-chat"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
