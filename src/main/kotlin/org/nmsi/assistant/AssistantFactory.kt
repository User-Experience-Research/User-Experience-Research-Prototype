package org.nmsi.assistant

import org.nmsi.data.SupportRepository

object AssistantFactory {
    fun create(repository: SupportRepository): SupportAssistant {
        val apiKey = System.getenv("OPENAI_API_KEY")?.trim().orEmpty()
        val fallback = RuleBasedSupportAssistant(repository)
        if (apiKey.isBlank()) return fallback

        return ResilientSupportAssistant(
            primary =
                OpenAiSupportAssistant(
                    repository = repository,
                    apiKey = apiKey,
                    model = System.getenv("OPENAI_MODEL")?.trim().orEmpty().ifBlank { "gpt-5.6-terra" },
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

