package no.nav.k9.los.aksjonspunktbehandling

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*
import kotlin.test.assertTrue


class K9TilbakeEventHandlerTest : AbstractK9LosIntegrationTest() {

    lateinit var k9TilbakeEventHandler: K9TilbakeEventHandler
    lateinit var oppgaveRepository: OppgaveRepository
    lateinit var aktivOppgaveRepository: AktivOppgaveRepository
    lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        get<OmrådeSetup>().setup()
        get<K9TilbakeTilLosAdapterTjeneste>().setup()
        k9TilbakeEventHandler = get<K9TilbakeEventHandler>()
        oppgaveRepository = get<OppgaveRepository>()
        aktivOppgaveRepository = get<AktivOppgaveRepository>()
        transactionalManager = get<TransactionalManager>()
    }

    @Test
    fun `Skal ikke lage aktiv oppgave når behandlingen har aktivt autopunkt`() {
        val json = lagK9TilbakeEvent(FagsakYtelseType.OMSORGSPENGER,
            mapOf(
                "5030" to AksjonspunktStatus.UTFØRT,
                "7002" to AksjonspunktStatus.UTFØRT,
                "7001" to AksjonspunktStatus.OPPRETTET,
                "7003" to AksjonspunktStatus.OPPRETTET
            ))

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave = oppgaveRepository.hent(UUID.fromString("29cbdc33-0e59-4559-96a8-c2154bf17e5a"))

        assertTrue { !oppgave.aktiv } //oppgaven er ikke aktiv, siden behandlngen har autopunkt 7001
        val oppgaveV3 = transactionalManager.transaction { tx ->
            aktivOppgaveRepository.hentOppgaveForEksternId(tx,EksternOppgaveId("K9", "29cbdc33-0e59-4559-96a8-c2154bf17e5a"))}
        assertThat(oppgaveV3!!.status).isEqualTo(Oppgavestatus.VENTER.kode)
    }

    @Test
    fun `Skal ikke lage aktiv oppgave for FRISINN`() {
        val json = lagK9TilbakeEvent(
            FagsakYtelseType.FRISINN,
            mapOf(
                "7003" to AksjonspunktStatus.UTFØRT,
                "5004" to AksjonspunktStatus.OPPRETTET
            )
        )

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave = oppgaveRepository.hent(UUID.fromString("29cbdc33-0e59-4559-96a8-c2154bf17e5a"))
        assertTrue { !oppgave.aktiv }
        val oppgaveV3 = transactionalManager.transaction { tx ->
            aktivOppgaveRepository.hentOppgaveForEksternId(tx,EksternOppgaveId("K9", "29cbdc33-0e59-4559-96a8-c2154bf17e5a"))}
        assertThat(oppgaveV3).isNull()
    }

    @Test
    fun `Skal lage aktiv oppgave for PSB`() {
        val json = lagK9TilbakeEvent(
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            mapOf(
                "7003" to AksjonspunktStatus.UTFØRT,
                "5004" to AksjonspunktStatus.OPPRETTET
            )
        )

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave = oppgaveRepository.hent(UUID.fromString("29cbdc33-0e59-4559-96a8-c2154bf17e5a"))
        assertTrue { oppgave.aktiv }
        val oppgaveV3 = transactionalManager.transaction { tx ->
            aktivOppgaveRepository.hentOppgaveForEksternId(tx,EksternOppgaveId("K9", "29cbdc33-0e59-4559-96a8-c2154bf17e5a"))}
        assertThat(oppgaveV3!!.status).isEqualTo(Oppgavestatus.AAPEN.kode)
    }

    @Test
    fun `Støtte tilbakekreving med beslutter aksjonspunkt`() {

        val json = lagK9TilbakeEvent(
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            mapOf(
                "7002" to AksjonspunktStatus.UTFØRT,
                "7003" to AksjonspunktStatus.UTFØRT,
                "5002" to AksjonspunktStatus.UTFØRT,
                "5003" to AksjonspunktStatus.UTFØRT,
                "5004" to AksjonspunktStatus.UTFØRT,
                "5005" to AksjonspunktStatus.OPPRETTET
            )
        )

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave = oppgaveRepository.hent(UUID.fromString("29cbdc33-0e59-4559-96a8-c2154bf17e5a"))
        assertTrue { oppgave.aktiv }
        assertTrue { oppgave.tilBeslutter }

        val oppgaveV3 = transactionalManager.transaction { tx ->
            aktivOppgaveRepository.hentOppgaveForEksternId(tx,EksternOppgaveId("K9", "29cbdc33-0e59-4559-96a8-c2154bf17e5a"))}
        assertThat(oppgaveV3!!.status).isEqualTo(Oppgavestatus.AAPEN.kode)
    }

    fun lagK9TilbakeEvent(ytelsetype: FagsakYtelseType, aksjonspunkter: Map<String, AksjonspunktStatus>): String {
        val aksjonspunkString = aksjonspunkter
            .map { (aksjonspunkt, status) -> "\"$aksjonspunkt\": \"${status.kode}\"" }
            .joinToString(",")
        @Language("JSON") val json =
            """{
          "eksternId": "29cbdc33-0e59-4559-96a8-c2154bf17e5a",
          "fagsystem": "K9TILBAKE",
          "saksnummer": "63P3S",
          "aktørId": "1073276027910",
          "behandlingId": null,
          "eventTid": "2020-09-11T11:50:51.189546",
          "eventHendelse": "AKSJONSPUNKT_OPPRETTET",
          "behandlinStatus": null,
          "behandlingStatus": "UTRED",
          "behandlingSteg": "FAKTFEILUTSTEG",
          "behandlendeEnhet": "4863",
          "ytelseTypeKode": "${ytelsetype.kode}",
          "behandlingTypeKode": "BT-007",
          "opprettetBehandling": "2020-09-11T11:50:49.025",
          "aksjonspunktKoderMedStatusListe": {$aksjonspunkString},
          "href": "/k9/tilbake/fagsak/63P3S/behandling/202/?punkt=default&fakta=default",
          "førsteFeilutbetaling": "2020-06-01",
          "feilutbetaltBeløp": 616,
          "ansvarligSaksbehandlerIdent": null
        }   """

        return json
    }
}
