package ai.videosearch.settings

import ai.videosearch.media.SignedMediaUrls
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider
import org.jboss.logging.Logger
import java.time.OffsetDateTime

enum class Source { PANEL, ENV, NONE }

/**
 * Valor efetivo de cada [Setting]: o que foi salvo no painel, senão o do ambiente, senão o padrão.
 * Fica em cache por poucos segundos, então uma troca no painel vale já na próxima busca.
 */
@ApplicationScoped
class RuntimeSettings {
    private val log = Logger.getLogger(RuntimeSettings::class.java)

    @Inject lateinit var signed: SignedMediaUrls

    @Volatile private var cache: Map<Setting, String> = emptyMap()
    @Volatile private var loadedAt = 0L

    private val box by lazy { SecretBox(signed.derivedKey("vps-settings")) }

    private fun overrides(): Map<Setting, String> {
        if (System.currentTimeMillis() - loadedAt < CACHE_MS) return cache
        synchronized(this) {
            if (System.currentTimeMillis() - loadedAt < CACHE_MS) return cache
            cache = QuarkusTransaction.requiringNew().call {
                AppSetting.listAll().mapNotNull { row ->
                    val setting = Setting.byId(row.key) ?: return@mapNotNull null
                    val value = if (setting.secret) box.decrypt(row.value).also {
                        if (it == null) log.warnf("não foi possível ler %s (chave de assinatura mudou?): informe a chave de novo em Configurações", row.key)
                    } else row.value
                    value?.takeIf { it.isNotEmpty() }?.let { setting to it }
                }.toMap()
            }
            loadedAt = System.currentTimeMillis()
        }
        return cache
    }

    private fun env(s: Setting): String =
        ConfigProvider.getConfig().getOptionalValue(s.configName, String::class.java).orElse("").trim()

    open fun get(s: Setting): String = overrides()[s] ?: env(s).ifEmpty { s.default }

    fun source(s: Setting): Source = when {
        overrides().containsKey(s) -> Source.PANEL
        env(s).isNotEmpty() -> Source.ENV
        else -> Source.NONE
    }

    /** `null` volta ao valor do ambiente; texto salva no painel. */
    fun update(changes: Map<Setting, String?>) {
        QuarkusTransaction.requiringNew().run {
            changes.forEach { (s, v) ->
                val row = AppSetting.findById(s.id)
                if (v.isNullOrBlank()) {
                    row?.delete()
                } else {
                    val stored = if (s.secret) box.encrypt(v.trim()) else v.trim()
                    (row ?: AppSetting().also { it.key = s.id }).apply {
                        value = stored
                        updatedAt = OffsetDateTime.now()
                        persist()
                    }
                }
            }
        }
        loadedAt = 0
    }

    companion object { private const val CACHE_MS = 5_000L }
}
