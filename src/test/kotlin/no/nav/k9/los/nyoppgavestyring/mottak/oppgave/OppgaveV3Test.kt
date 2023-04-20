package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.*
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime

class OppgaveV3Test : AbstractK9LosIntegrationTest() {

    private val områdeDto = Område(eksternId = "K9")
    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var områdeRepository: OmrådeRepository
    private lateinit var feltdefinisjonTjeneste: FeltdefinisjonTjeneste
    private lateinit var oppgavetypeTjeneste: OppgavetypeTjeneste
    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        oppgaveV3Tjeneste = get()
        områdeRepository = get()
        feltdefinisjonTjeneste = get()
        oppgavetypeTjeneste = get()
        byggOppgavemodell()
        transactionalManager = get()
    }

    @Test
    fun `test at oppgave ikke blir opprettet om området ikke finnes`() {
        val innkommendeOppgaveMedUkjentOmråde = lagOppgaveDto().copy(område = "ukjent-område")
        assertThrows<IllegalArgumentException>("Området finnes ikke") {
            transactionalManager.transaction { tx ->
                oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(innkommendeOppgaveMedUkjentOmråde, tx)
            }
        }
    }

    @Test
    fun `test at oppgave ikke blir opprettet om den inneholder felter som ikke finnes i oppgavetype`() {
        val ukjentOppgaveFeltVerdi = OppgaveFeltverdiDto("ukjent", "verdi")
        val oppgaveDto = lagOppgaveDto()
        val oppgaveDtoMedUkjentFeltVerdi =
            oppgaveDto.copy(feltverdier = oppgaveDto.feltverdier.plus(ukjentOppgaveFeltVerdi))

        assertThrows<IllegalArgumentException>("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi: ${ukjentOppgaveFeltVerdi.nøkkel}\"") {
            transactionalManager.transaction { tx ->
                oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDtoMedUkjentFeltVerdi, tx)
            }
        }

    }

    @Test
    fun `test at oppgave ikke blir opprettet om den mangler obligatoriske felter`() {
        val feilOppgaveFeltverdi = OppgaveFeltverdiDto(nøkkel = "aktorId", verdi = "test")
        val oppgaveDto = lagOppgaveDto()
        val oppgaveSomManglerObligatoriskFelt = oppgaveDto.copy(feltverdier = listOf(feilOppgaveFeltverdi))
        assertThrows<IllegalArgumentException>("Kan ikke oppgi feltverdi som ikke er spesifisert i oppgavetypen: ${feilOppgaveFeltverdi.nøkkel}\"") {
            transactionalManager.transaction { tx ->
                oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveSomManglerObligatoriskFelt, tx)
            }
        }
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
                    område = område,
                    feltdefinisjoner = Feltdefinisjoner(
                        feltdefinisjonerDto = feltdefinisjonDto,
                        område = område
                    )
                )
            )
        }
    }

    private fun byggOppgavemodell() {
        if (områdeRepository.hent(områdeDto.eksternId) == null) {
            områdeRepository.lagre(eksternId = områdeDto.eksternId)
            feltdefinisjonTjeneste.oppdater(lagFeltdefinisjonDto())
            oppgavetypeTjeneste.oppdater(lagOppgavetypeDto())
        }
    }

    private fun lagFeltdefinisjonDto(): FeltdefinisjonerDto {
        return FeltdefinisjonerDto(
            område = områdeDto.eksternId,
            feltdefinisjoner = setOf(
                FeltdefinisjonDto(
                    id = "aksjonspunkt",
                    listetype = true,
                    tolkesSom = "String",
                    true,
                    kodeverk = null
                ),
                FeltdefinisjonDto(
                    id = "opprettet",
                    listetype = false,
                    tolkesSom = "Date",
                    true,
                    kodeverk = null
                ),
                FeltdefinisjonDto(
                    id = "aktorId",
                    listetype = false,
                    tolkesSom = "String",
                    true,
                    kodeverk = null
                ),
                FeltdefinisjonDto(
                    id = "akkumulertVentetidSaksbehandler",
                    listetype = false,
                    tolkesSom = "Duration",
                    false,
                    kodeverk = null
                ),
                FeltdefinisjonDto(
                    id = "avventerSaksbehandler",
                    listetype = false,
                    tolkesSom = "boolean",
                    false,
                    kodeverk = null
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

    private fun lagOppgaveDto(): OppgaveDto {
        return OppgaveDto(
            id = "aksjonspunkt",
            versjon = LocalDateTime.now().toString(),
            område = områdeDto.eksternId,
            kildeområde = "k9-sak-til-los",
            type = "aksjonspunkt",
            status = "ÅPEN",
            endretTidspunkt = LocalDateTime.now(),
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