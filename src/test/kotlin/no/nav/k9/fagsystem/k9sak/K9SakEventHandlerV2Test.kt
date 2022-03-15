package no.nav.k9.fagsystem.k9sak

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.k9.AbstractPostgresTest
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.integrasjon.azuregraph.AzureGraphService
import no.nav.k9.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringAksjonspunktHendelse
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringBehandlingOpprettetHendelse
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.Periode
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class K9SakEventHandlerV2Test : AbstractPostgresTest(), KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig(dataSource))
    }

    val saksbehandler = "Z123456"

    @Test
    fun skalIkkeFeileVedFeilendeOppslagMotAzure() {
        val accessToken = mockk<AccessTokenClient>()
        coEvery { accessToken.getAccessToken(any()) } throws Exception("FORVENTET TESTFEIL")

        val azureGraphService = AzureGraphService(accessToken)
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val eventHandler = K9sakEventHandlerV2(oppgaveTjenesteV2, AksjonspunktHendelseMapper(azureGraphService))

        val eksternId = UUID.randomUUID()

        runBlocking {
            eventHandler.prosesser(lagOpprettetHendelse(eksternId = eksternId))
            eventHandler.prosesser(lagOppgaveOpprettet(eksternId = eksternId))
            eventHandler.prosesser(lagOppgaveAvsluttet(eksternId = eksternId))
        }

        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()
        val behandling = oppgaveRepositoryV2.hentBehandling(eksternId.toString())
        val utførtOppgave = behandling!!.oppgaver().first()
        assertThat(utførtOppgave.ferdigstilt?.behandlendeEnhet).isEqualTo("UKJENT")
        assertThat(utførtOppgave.ferdigstilt?.ansvarligSaksbehandlerIdent).isEqualTo(saksbehandler)
    }

    @Test
    fun skalSlåOppBehandlingeEnhetVedFerdigstillelse() {
        val oppgaveTjenesteV2 = get<OppgaveTjenesteV2>()
        val eventHandler = K9sakEventHandlerV2(oppgaveTjenesteV2, AksjonspunktHendelseMapper(get()))

        val eksternId = UUID.randomUUID()
        runBlocking {
            eventHandler.prosesser(lagOpprettetHendelse(eksternId = eksternId))
            eventHandler.prosesser(lagOppgaveOpprettet(eksternId = eksternId))
            eventHandler.prosesser(lagOppgaveAvsluttet(eksternId = eksternId))
        }

        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()
        val behandling = oppgaveRepositoryV2.hentBehandling(eksternId.toString())
        assertThat(behandling!!.oppgaver().first().ferdigstilt?.behandlendeEnhet).isEqualTo("3450")
    }


    fun lagOpprettetHendelse(eksternId: UUID): ProduksjonsstyringBehandlingOpprettetHendelse {
        return ProduksjonsstyringBehandlingOpprettetHendelse(
            eksternId,
            LocalDateTime.now(),
            "SAKSNUMMER",
            FagsakYtelseType.PSB,
            BehandlingType.FØRSTEGANGSSØKNAD,
            null,
            Periode(LocalDate.now(), LocalDate.now().plusWeeks(1)),
            AktørId.dummy(),
            AktørId.dummy(),
            AktørId.dummy()
        )
    }

    fun lagOppgaveOpprettet(eksternId: UUID): ProduksjonsstyringAksjonspunktHendelse {
        return ProduksjonsstyringAksjonspunktHendelse(
            eksternId,
            LocalDateTime.now(),
            listOf(AksjonspunktTilstandDto(
                "5015",
                AksjonspunktStatus.OPPRETTET,
                null,
                null,
                null
            ))
        )
    }

    fun lagOppgaveAvsluttet(eksternId: UUID): ProduksjonsstyringAksjonspunktHendelse {
        return ProduksjonsstyringAksjonspunktHendelse(
            eksternId,
            LocalDateTime.now(),
            listOf(AksjonspunktTilstandDto(
                "5015",
                AksjonspunktStatus.UTFØRT,
                null,
                saksbehandler,
                null
            ))
        )
    }
}