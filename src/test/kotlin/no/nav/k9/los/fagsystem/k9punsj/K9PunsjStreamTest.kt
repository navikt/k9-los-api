package no.nav.k9.los.fagsystem.k9punsj

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.AzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.sak.typer.AktørId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class K9PunsjStreamTest {

    val eksternId = UUID.randomUUID()
    lateinit var eventHandler: K9PunsjEventHandlerV2

    @BeforeEach
    fun setup() {
        val azure = mockk<AzureGraphService>()
        coEvery { azure.hentEnhetForBrukerMedSystemToken(any()) } returns "12345"
        val oppgaveTjenesteV2 = mockk<OppgaveTjenesteV2>(relaxed = true)
        eventHandler = K9PunsjEventHandlerV2(oppgaveTjenesteV2, azure)
    }

    @Test
    fun k9PunsjStreamSkalKunneMottaOpprettetOppgave() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "journalpostId": "123456",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "PUNSJ_OPPRETTET",
            "ytelseType": null,
            "behandlingType": null,
            "behandlingstidFrist": "2020-02-20",
            "søkersAktørId": "${AktørId.dummy()}",
            "pleietrengendeAktørId": null
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
        }
    }

    @Test
    fun k9PunsjStreamSkalKunneFerdigstilleOppgaver() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "journalpostId": "123456",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "PUNSJ_FERDIGSTILT",
            "ferdigstiltAv": "Z992837",
            "sendtInn": true
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
        }
    }

    @Test
    fun k9PunsjStreamSkalKunneAvbryteOppgaver() {
        @Language("JSON") val input = """{
                "eksternId": "$eksternId",
                "journalpostId": "123456",
                "hendelseTid": "2020-02-20T07:38:49",
                "hendelseType": "PUNSJ_AVBRUTT"
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
        }
    }
}