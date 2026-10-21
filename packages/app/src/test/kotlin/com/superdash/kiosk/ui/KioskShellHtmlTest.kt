package com.superdash.kiosk.ui

import com.superdash.ha.HaTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class KioskShellHtmlTest {
    private val template = "<script>__SUPERDASH_TOKEN_SEED__</script><iframe src=\"__SUPERDASH_HA_URL__\">"

    @Test fun `seeds tokens before the iframe and escapes script breakouts`() {
        data class Case(
            val name: String,
            val tokens: HaTokens?,
            val expected: String,
        )
        val seedPrefix = "(function() {\n    try {\n        localStorage.setItem('hassTokens', JSON.stringify("
        val seedSuffix = "));\n    } catch (e) {}\n})();"
        val cases =
            listOf(
                Case(
                    "tokens present",
                    HaTokens("access", "refresh", 100_000L),
                    "<script>" + seedPrefix +
                        "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":90," +
                        "\"hassUrl\":\"https://ha.local\",\"clientId\":\"https://ha.local/\"," +
                        "\"expires\":100000}" + seedSuffix +
                        "</script><iframe src=\"https://ha.local/lovelace\">",
                ),
                Case(
                    "tokens null",
                    null,
                    "<script></script><iframe src=\"https://ha.local/lovelace\">",
                ),
                Case(
                    "script breakout and quotes",
                    HaTokens("a</script><b>\"'", "r<!--", 100_000L),
                    "<script>" + seedPrefix +
                        "{\"access_token\":\"a\\u003c/script>\\u003cb>\\\"'\"," +
                        "\"refresh_token\":\"r\\u003c!--\",\"expires_in\":90," +
                        "\"hassUrl\":\"https://ha.local\",\"clientId\":\"https://ha.local/\"," +
                        "\"expires\":100000}" + seedSuffix +
                        "</script><iframe src=\"https://ha.local/lovelace\">",
                ),
                Case(
                    "placeholder text inside a token",
                    HaTokens("__SUPERDASH_HA_URL__", "r", 100_000L),
                    "<script>" + seedPrefix +
                        "{\"access_token\":\"__SUPERDASH_HA_URL__\",\"refresh_token\":\"r\",\"expires_in\":90," +
                        "\"hassUrl\":\"https://ha.local\",\"clientId\":\"https://ha.local/\"," +
                        "\"expires\":100000}" + seedSuffix +
                        "</script><iframe src=\"https://ha.local/lovelace\">",
                ),
            )
        cases.forEach { case ->
            // Android's JSONObject escapes every slash, the unit test library only after "<".
            val html = buildShellHtml(template, "https://ha.local/lovelace", "https://ha.local", case.tokens, 10_000L)
            assertEquals(case.name, case.expected, html.replace("\\/", "/"))
        }
    }
}
