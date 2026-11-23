package ai.videosearch.settings

/**
 * Configurações que o painel pode alterar. Cada uma tem um valor padrão vindo do ambiente (`configName`, os `VPS_*`),
 * que o painel sobrescreve; remover a alteração do painel volta ao valor do ambiente.
 */
enum class Setting(val id: String, val configName: String, val secret: Boolean = false, val default: String = "", val choices: List<String>? = null) {
    AI_PROVIDER("ai.provider", "vps.ai.provider", default = "gemini", choices = listOf("gemini", "deepseek")),
    AI_GEMINI_KEY("ai.gemini.api-key", "vps.ai.gemini-api-key", secret = true),
    AI_GEMINI_MODEL("ai.gemini.model", "vps.ai.gemini-model", default = "gemini-2.5-flash"),
    AI_DEEPSEEK_KEY("ai.deepseek.api-key", "vps.ai.deepseek-api-key", secret = true),
    AI_DEEPSEEK_BASE_URL("ai.deepseek.base-url", "vps.ai.deepseek-base-url", default = "https://api.deepseek.com"),
    AI_DEEPSEEK_MODEL("ai.deepseek.model", "vps.ai.deepseek-model", default = "deepseek-flash"),

    SEARCH_PROVIDER("search.provider", "vps.search.provider", default = "serpapi", choices = listOf("serpapi", "brave", "deepseek")),
    SEARCH_SERPAPI_KEY("search.serpapi.key", "vps.search.serpapi-key", secret = true),
    SEARCH_BRAVE_KEY("search.brave.key", "vps.search.brave-key", secret = true),
    SEARCH_DEEPSEEK_KEY("search.deepseek.api-key", "vps.search.deepseek-api-key", secret = true),
    SEARCH_DEEPSEEK_BASE_URL("search.deepseek.base-url", "vps.search.deepseek-base-url"),
    SEARCH_DEEPSEEK_MODEL("search.deepseek.model", "vps.search.deepseek-model");

    val isUrl get() = id.endsWith("base-url")

    companion object {
        fun byId(id: String): Setting? = entries.firstOrNull { it.id == id }
    }
}
