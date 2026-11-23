package ai.videosearch.settings

import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.path.json.JsonPath
import jakarta.inject.Inject
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class SettingsResourceTest {
    @Inject lateinit var settings: RuntimeSettings

    private fun api() = given().header("Authorization", "Bearer senha-de-teste").contentType(ContentType.JSON)

    private fun view(): Map<String, Map<String, Any?>> =
        JsonPath(api().get("/api/settings").then().statusCode(200).extract().asString())
            .getList<Map<String, Any?>>("settings").associateBy { it["id"] as String }

    @AfterEach
    fun clean() {
        QuarkusTransaction.requiringNew().run { AppSetting.deleteAll() }
        settings.update(emptyMap())
    }

    @Test
    fun `exige a senha do painel`() {
        given().get("/api/settings").then().statusCode(401)
        given().contentType(ContentType.JSON).body("""{"values":{}}""").put("/api/settings").then().statusCode(401)
    }

    @Test
    fun `salva provedor e chave, usa na hora e nunca devolve a chave`() {
        val secret = "AIzaSyDUMMY-1234567890-abcd"
        api().body("""{"values":{"ai.provider":"deepseek","ai.deepseek.api-key":"$secret","ai.deepseek.base-url":"https://openrouter.ai/api/v1"}}""")
            .put("/api/settings").then().statusCode(200).body(org.hamcrest.Matchers.not(containsString(secret)))

        val v = view()
        assertEquals("deepseek", v.getValue("ai.provider")["value"])
        assertEquals("PANEL", v.getValue("ai.provider")["source"])
        assertEquals(true, v.getValue("ai.deepseek.api-key")["configured"])
        assertEquals(null, v.getValue("ai.deepseek.api-key")["value"])
        assertEquals("••••abcd", v.getValue("ai.deepseek.api-key")["hint"])
        assertEquals("https://openrouter.ai/api/v1", v.getValue("ai.deepseek.base-url")["value"])

        // vale já na próxima chamada, e /api/config reflete a escolha
        assertEquals(secret, settings.get(Setting.AI_DEEPSEEK_KEY))
        api().get("/api/config").then().statusCode(200)
            .body("aiProvider", org.hamcrest.Matchers.equalTo("deepseek"))
            .body("aiConfigured", org.hamcrest.Matchers.equalTo(true))
    }

    @Test
    fun `a chave fica cifrada no banco`() {
        api().body("""{"values":{"search.serpapi.key":"serp-chave-longa-9999"}}""").put("/api/settings").then().statusCode(200)
        val stored = QuarkusTransaction.requiringNew().call { AppSetting.findById("search.serpapi.key")!!.value }
        assertFalse(stored.contains("serp-chave"))
        assertTrue(stored.startsWith(SecretBox.PREFIX))
    }

    @Test
    fun `null remove a alteracao e volta ao ambiente`() {
        api().body("""{"values":{"search.provider":"brave"}}""").put("/api/settings").then().statusCode(200)
        assertEquals("brave", settings.get(Setting.SEARCH_PROVIDER))
        api().body("""{"values":{"search.provider":null}}""").put("/api/settings").then().statusCode(200)
        assertEquals("serpapi", settings.get(Setting.SEARCH_PROVIDER))
        assertTrue(view().getValue("search.provider")["source"] != "PANEL")
    }

    @Test
    fun `campo ausente fica como esta`() {
        api().body("""{"values":{"ai.gemini.model":"gemini-x","search.brave.key":"brave-chave-longa-1111"}}""").put("/api/settings").then().statusCode(200)
        api().body("""{"values":{"ai.gemini.model":"gemini-y"}}""").put("/api/settings").then().statusCode(200)
        assertEquals("gemini-y", settings.get(Setting.AI_GEMINI_MODEL))
        assertEquals("brave-chave-longa-1111", settings.get(Setting.SEARCH_BRAVE_KEY))
    }

    @Test
    fun `recusa valores invalidos`() {
        api().body("""{"values":{"ai.provider":"openai"}}""").put("/api/settings").then().statusCode(400)
        api().body("""{"values":{"ai.deepseek.base-url":"file:///etc/passwd"}}""").put("/api/settings").then().statusCode(400)
        api().body("""{"values":{"ai.deepseek.base-url":"https://user:pw@x.com"}}""").put("/api/settings").then().statusCode(400)
        api().body("""{"values":{"ai.gemini.api-key":"com espaço"}}""").put("/api/settings").then().statusCode(400)
        api().body("""{"values":{"nao.existe":"x"}}""").put("/api/settings").then().statusCode(400)
        assertEquals("gemini", settings.get(Setting.AI_PROVIDER))
    }
}
