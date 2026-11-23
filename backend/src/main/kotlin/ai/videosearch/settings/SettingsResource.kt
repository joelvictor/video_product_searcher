package ai.videosearch.settings

import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.net.URI

data class SettingView(
    val id: String,
    val secret: Boolean,
    val choices: List<String>?,
    /** Nunca devolve chaves: só diz se há uma e mostra o final dela. Campos que não são segredo trazem o valor. */
    val value: String?,
    val hint: String?,
    val configured: Boolean,
    val source: Source,
)

data class SettingsView(val settings: List<SettingView>)

/** `null` remove a alteração do painel (volta ao ambiente); texto salva; campos ausentes ficam como estão. */
data class SettingsInput(val values: Map<String, String?> = emptyMap())

/** Provedor e chaves da IA e da busca, editáveis no painel. Fica atrás da mesma senha de `/api`. */
@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SettingsResource {
    @Inject lateinit var settings: RuntimeSettings

    @GET
    fun get() = view()

    @PUT
    fun update(input: SettingsInput): SettingsView {
        val changes = input.values.mapKeys { (id, _) -> Setting.byId(id) ?: throw BadRequestException("Configuração desconhecida: $id") }
        changes.forEach { (s, v) -> validate(s, v) }
        settings.update(changes)
        return view()
    }

    private fun validate(s: Setting, v: String?) {
        val value = v?.trim().orEmpty()
        if (value.isEmpty()) return
        s.choices?.let { if (value.lowercase() !in it) throw BadRequestException("Valor inválido para ${s.id}. Use: ${it.joinToString(", ")}") }
        if (s.isUrl) {
            val uri = runCatching { URI(value) }.getOrNull()
            if (uri?.scheme?.lowercase() !in setOf("http", "https") || uri?.host.isNullOrBlank() || uri?.userInfo != null) {
                throw BadRequestException("Informe uma URL http ou https válida (sem usuário e senha na URL).")
            }
        }
        if (s.secret && value.any { it.isWhitespace() || it.isISOControl() }) throw BadRequestException("A chave não pode ter espaços nem quebras de linha.")
        if (value.length > 2000) throw BadRequestException("Valor grande demais.")
    }

    private fun view() = SettingsView(
        Setting.entries.map { s ->
            val value = settings.get(s)
            SettingView(
                id = s.id,
                secret = s.secret,
                choices = s.choices,
                value = if (s.secret) null else value,
                hint = if (s.secret && value.isNotEmpty()) "••••" + (if (value.length >= 12) value.takeLast(4) else "") else null,
                configured = value.isNotEmpty(),
                source = settings.source(s),
            )
        },
    )
}
