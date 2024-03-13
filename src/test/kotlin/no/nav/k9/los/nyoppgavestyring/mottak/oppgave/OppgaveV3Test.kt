package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveStatus
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonDto
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.*
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import kotlin.test.assertEquals

class OppgaveV3Test : AbstractK9LosIntegrationTest() {

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
    }

    @Test
    fun `test at oppgave ikke blir opprettet om området ikke finnes`() {
        val innkommendeOppgaveMedUkjentOmråde = lagOppgaveDto().copy(område = "ukjent-område")
        val exception = assertThrows<IllegalArgumentException> {
            transactionalManager.transaction { tx ->
                oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innkommendeOppgaveMedUkjentOmråde, tx)
            }
        }
        assertEquals("Området finnes ikke: ukjent-område", exception.message!!)
    }

    @Test
    fun `test at oppgave ikke blir opprettet om den inneholder felter som ikke finnes i oppgavetype`() {
        val ukjentOppgaveFeltVerdi = OppgaveFeltverdiDto("ukjent", "verdi")
        val oppgaveDto = lagOppgaveDto()
        val oppgaveDtoMedUkjentFeltVerdi =
            oppgaveDto.copy(feltverdier = oppgaveDto.feltverdier.plus(ukjentOppgaveFeltVerdi))

        val exception =
            assertThrows<IllegalArgumentException> {
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDtoMedUkjentFeltVerdi, tx)
                }
            }

        assertEquals(
            "Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi: ${ukjentOppgaveFeltVerdi.nøkkel}",
            exception.message!!
        )

    }

    @Test
    fun `test at oppgave ikke blir opprettet om den mangler obligatoriske felter`() {
        val feilOppgaveFeltverdi = OppgaveFeltverdiDto(nøkkel = "aktorId", verdi = "test")
        val oppgaveDto = lagOppgaveDto()
        val oppgaveSomManglerObligatoriskFelt = oppgaveDto.copy(feltverdier = listOf(feilOppgaveFeltverdi))
        val exception =
            assertThrows<IllegalArgumentException> {
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveSomManglerObligatoriskFelt, tx)
                }
            }

        assertEquals(
            "Oppgaven mangler obligatorisk felt opprettet",
            exception.message!!
        )
    }

    @Test
    fun `test at vi ikke logger aktørid`() {
        val område = Område(eksternId = områdeDto.eksternId)
        val oppgaveDto = lagOppgaveDtoMedManglendeVerdiIObligFelt()
        val oppgaveTypeDto = lagOppgavetypeDto()
        val feltdefinisjonDto = lagFeltdefinisjonDto()

        assertThrows<IllegalArgumentException> {
            OppgaveV3(
                oppgaveDto = oppgaveDto,
                oppgavetype = Oppgavetype(
                    dto = oppgaveTypeDto.oppgavetyper.first(),
                    definisjonskilde = "k9-sak-til-los",
                    oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}?fakta=default&punkt=default",
                    område = område,
                    feltdefinisjoner = Feltdefinisjoner(
                        feltdefinisjonerDto = feltdefinisjonDto,
                        område = område
                    )
                )
            )
        }
    }

    @Test
    fun `hvis alle oppgaver på reservasjonsnøkkel er lukket skal reservasjon annulleres -- kun en oppgave`() {
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(Oppgavestatus.AAPEN.toString()), tx)
        }
        verify(exactly = 0) { reservasjonV3Tjenestemock.annullerReservasjonHvisFinnes(any(), any(), any()) }
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(Oppgavestatus.LUKKET.toString()), tx)
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
                ),
                FeltdefinisjonDto(
                    id = "aktorId",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "String",
                    true,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,

                ),
                FeltdefinisjonDto(
                    id = "akkumulertVentetidSaksbehandler",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "Duration",
                    false,
                    false,
                    kodeverkreferanse = null,
                    transientFeltutleder = null,
                ),
                FeltdefinisjonDto(
                    id = "avventerSaksbehandler",
                    visningsnavn = "Test",
                    listetype = false,
                    tolkesSom = "boolean",
                    false,
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
                        ),
                        OppgavefeltDto(
                            id = "aktorId",
                            visPåOppgave = true,
                            påkrevd = true
                        ),
                        OppgavefeltDto(
                            id = "akkumulertVentetidSaksbehandler",
                            visPåOppgave = false,
                            påkrevd = true,
                            feltutleder = "no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.AkkumulertVentetidSaksbehandler",
                        ),
                        OppgavefeltDto(
                            id = "avventerSaksbehandler",
                            visPåOppgave = false,
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
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                )
            )
        )
    }

    private fun lagOppgaveDtoMedManglendeVerdiIObligFelt(): OppgaveDto {
        return OppgaveDto(
            id = "aksjonspunkt",
            versjon = LocalDateTime.now().toString(),
            område = områdeDto.eksternId,
            kildeområde = "k9-sak-til-los",
            type = "aksjonspunkt",
            status = "ÅPEN",
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "test",
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "opprettet",
                    verdi = null
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                )
            )
        )
    }

}