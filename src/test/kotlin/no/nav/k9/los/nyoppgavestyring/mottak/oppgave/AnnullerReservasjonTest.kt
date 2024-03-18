package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavefeltDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetyperDto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

@Disabled
class AnnullerReservasjonTest: AbstractK9LosIntegrationTest() {
    private val områdeDto = Område(eksternId = "OppgaveV3Test")
    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var områdeRepository: OmrådeRepository
    private lateinit var feltdefinisjonTjeneste: FeltdefinisjonTjeneste
    private lateinit var oppgavetypeTjeneste: OppgavetypeTjeneste
    private lateinit var transactionalManager: TransactionalManager
    private var reservasjonV3Tjenestemock = mockk<ReservasjonV3Tjeneste>()

    @BeforeEach
    fun setup() {
        oppgaveV3Tjeneste = OppgaveV3Tjeneste(
            oppgaveV3Repository = get(),
            oppgavetypeRepository = get(),
            områdeRepository = get(),
            reservasjonTjeneste = reservasjonV3Tjenestemock,
        )
        områdeRepository = get()
        feltdefinisjonTjeneste = get()
        oppgavetypeTjeneste = get()
        byggOppgavemodell()
        transactionalManager = get()

        justRun { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
    }

    private fun byggOppgavemodell() {
        områdeRepository.lagre(eksternId = områdeDto.eksternId)
        oppgavetypeTjeneste.oppdater(
            OppgavetyperDto(
                "K9",
                definisjonskilde = "unittest",
                oppgavetyper = emptySet()
            )
        )
        feltdefinisjonTjeneste.oppdater(lagFeltdefinisjonDto())
        oppgavetypeTjeneste.oppdater(lagOppgavetypeDto())
    }

    private fun lagFeltdefinisjonDto(): FeltdefinisjonerDto {
        return FeltdefinisjonerDto(
            område = områdeDto.eksternId,
            feltdefinisjoner = setOf(
                FeltdefinisjonDto(
                    id = "aksjonspunkt",
                    visningsnavn = "Test",
                    listetype = true,
                    tolkesSom = "String",
                    true,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "opprettet",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "Date",
                    true,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                )
            )
        )
    }

    private fun lagOppgavetypeDto(): OppgavetyperDto {
        return OppgavetyperDto(
            område = områdeDto.eksternId,
            definisjonskilde = "k9-sak-til-los",
            oppgavetyper = setOf(
                OppgavetypeDto(
                    id = "aksjonspunkt",
                    oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}?fakta=default&punkt=default",
                    oppgavefelter = setOf(
                        OppgavefeltDto(
                            id = "aksjonspunkt",
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        OppgavefeltDto(
                            id = "opprettet",
                            visPåOppgave = true,
                            påkrevd = true
                        )
                    )
                )
            )
        )
    }

    private fun lagOppgaveDto(id: String = "test", reservasjonsnøkkel: String = "test", status: String = "AAPEN"): OppgaveDto {
        return OppgaveDto(
            id = id,
            versjon = LocalDateTime.now().toString(),
            område = områdeDto.eksternId,
            kildeområde = "k9-sak-til-los",
            type = "aksjonspunkt",
            status = status,
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = reservasjonsnøkkel,
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "opprettet",
                    verdi = LocalDateTime.now().toString()
                )
            )
        )
    }


    @Test
    fun `hvis alle oppgaver på reservasjonsnøkkel er lukket skal reservasjon annulleres -- kun en oppgave`() {
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(status = Oppgavestatus.AAPEN.toString()), tx)
        }
        verify(exactly = 0) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(status = Oppgavestatus.LUKKET.toString()), tx)
        }
        verify(exactly = 1) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
    }

    @Test
    fun `hvis alle oppgaver på reservasjonsnøkkel er lukket skal reservasjon annulleres -- to oppgaver`() {
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(
                id = "test1",
                status = Oppgavestatus.AAPEN.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(
                id = "test2",
                status = Oppgavestatus.AAPEN.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(
                id = "test2",
                status = Oppgavestatus.LUKKET.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        verify(exactly = 0) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(
                id = "test1",
                status = Oppgavestatus.LUKKET.toString(),
                reservasjonsnøkkel = "felles"
            ), tx)
        }
        verify(exactly = 1) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
    }
}