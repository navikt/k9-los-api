package no.nav.k9.los.fagsystem.k9sak

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.AzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*

class K9SakStreamTest {

    val eksternId = UUID.randomUUID()
    lateinit var eventHandler: K9sakEventHandlerV2

    @BeforeEach
    fun setup() {
        val azure = mockk<AzureGraphService>()
        coEvery { azure.hentEnhetForBrukerMedSystemToken(any()) } returns "12345"
        val aksjonspunktHendelseMapper = AksjonspunktHendelseMapper(azure)
        val oppgaveTjenesteV2 = mockk<OppgaveTjenesteV2>(relaxed = true)
        eventHandler = K9sakEventHandlerV2(oppgaveTjenesteV2, aksjonspunktHendelseMapper)
    }

    @Test
    fun k9SakStreamSkalKunneMottaAksjonspunkthendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "AKSJONSPUNKT",
             "aksjonspunktTilstander": []
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
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
            "behandlingType": "BT-004",
            "behandlingstidFrist": "2021-04-05",
            "fagsakPeriode": null,
            "søkersAktørId": "1111111",
            "pleietrengendeAktørId": "123456",
            "relatertPartAktørId": "54321"
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
        }
    }

    @Test
    fun k9SakStreamSkalKunneMottaBehandlingAvsluttethendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "BEHANDLING_AVSLUTTET",
            "behandlingResultatType": "INNVILGET"
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
        }
    }


    @Disabled
    @Test
    fun k9SakStreamSkalKunneMottaKravdokumenthendelser() {
        @Language("JSON") val input = """{
            "eksternId": "$eksternId",
            "hendelseTid": "2020-02-20T07:38:49",
            "hendelseType": "KRAVDOKUMENT",
            "kravdokumenter": []
        }""".trimIndent()
        runBlocking {
            eventHandler.prosesser(LosObjectMapper.instance.readValue(input))
        }
    }
}