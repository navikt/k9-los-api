package no.nav.k9.aksjonspunktbehandling.k9sak

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.aksjonspunktbehandling.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.Ignore
import org.junit.Test
import java.util.*

class K9SakStreamTest {

    val eksternId = UUID.randomUUID()
    val eventHandler = K9sakEventHandlerV2(mockk(), mockk())

    @Test
    fun k9SakStreamSkalKunneMottaAksjonspunkthendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "AKSJONSPUNKT",
             "aksjonspunktTilstander": []
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(objectMapper().readValue(input))
        }
    }

    @Test
    fun k9SakStreamSkalKunneMottaBehandlingOpprettethendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "BEHANDLING_OPPRETTET",
            "saksnummer": "SAKSNUMMER",
            "ytelseType": "PSB",
            "behandlingType": "REVURDERING",
            "behandlingstidFrist": "2021-04-05",
            "fagsakPeriode": null,
            "søkersAktørId": "1111111",
            "pleietrengendeAktørId": "123456",
            "relatertPartAktørId": "54321"
             "aksjonspunktTilstander": []
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(objectMapper().readValue(input))
        }
    }

    @Test
    fun k9SakStreamSkalKunneMottaBehandlingAvsluttethendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "BEHANDLING_AVSLUTTET",
             "aksjonspunktTilstander": []
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(objectMapper().readValue(input))
        }
    }


    @Ignore
    @Test
    fun k9SakStreamSkalKunneMottaKravdokumenthendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "KRAVDOKUMENT",
            "kravdokumenter": []
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(objectMapper().readValue(input))
        }
    }
}