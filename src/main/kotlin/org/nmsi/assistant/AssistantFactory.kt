package org.nmsi.assistant

import org.nmsi.data.SupportRepository

object AssistantFactory {
    fun create(repository: SupportRepository): SupportAssistant {
        val apiKey = System.getenv("DEEPSEEK_API_KEY")?.trim().orEmpty()
        val fallback = RuleBasedSupportAssistant(repository)
        if (apiKey.isBlank()) return fallback

        return ResilientSupportAssistant(
            primary =
                DeepSeekSupportAssistant(
                    repository = repository,
                    apiKey = apiKey,
                    model = System.getenv("DEEPSEEK_MODEL")?.trim().orEmpty().ifBlank { "deepseek-v4-flash" },
                ),
            fallback = fallback,
        )
    }
}

private class ResilientSupportAssistant(
    private val primary: SupportAssistant,
    private val fallback: SupportAssistant,
) : SupportAssistant {
    override suspend fun respond(
        userId: Long,
        message: String,
    ): AssistantReply =
        runCatching { primary.respond(userId, message) }
            .getOrElse { fallback.respond(userId, message) }
}
