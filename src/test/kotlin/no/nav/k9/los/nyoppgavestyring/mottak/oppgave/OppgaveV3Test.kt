package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import kotlin.test.assertEquals

class OppgaveV3Test : AbstractK9LosIntegrationTest() {

    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var oppgavemodellBuilder: RedusertOppgaveTestmodellBuilder
    private lateinit var gyldigeFeltutledere: GyldigeFeltutledere

    @BeforeEach
    fun setup() {
        oppgaveV3Tjeneste = get()
        transactionalManager = get()
        gyldigeFeltutledere = get()
        oppgavemodellBuilder = RedusertOppgaveTestmodellBuilder()
        oppgavemodellBuilder.byggOppgavemodell()
    }

    @Test
    fun `test at oppgave ikke blir opprettet om området ikke finnes`() {
        val innkommendeOppgaveMedUkjentOmråde = oppgavemodellBuilder.lagOppgaveDto().copy(område = "ukjent-område")
        val exception = assertThrows<IllegalArgumentException> {
            transactionalManager.transaction { tx ->
                oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(innkommendeOppgaveMedUkjentOmråde), tx)
            }
        }
        assertEquals("Området finnes ikke: ukjent-område", exception.message!!)
    }

    @Test
    fun `test at oppgave ikke blir opprettet om den inneholder felter som ikke finnes i oppgavetype`() {
        val ukjentOppgaveFeltVerdi = OppgaveFeltverdiDto("ukjent", "verdi")
        val oppgaveDto = oppgavemodellBuilder.lagOppgaveDto()
        val oppgaveDtoMedUkjentFeltVerdi =
            oppgaveDto.copy(feltverdier = oppgaveDto.feltverdier.plus(ukjentOppgaveFeltVerdi))

        val exception =
            assertThrows<IllegalArgumentException> {
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgaveDtoMedUkjentFeltVerdi), tx)
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
        val oppgaveDto = oppgavemodellBuilder.lagOppgaveDto()
        val oppgaveSomManglerObligatoriskFelt = oppgaveDto.copy(feltverdier = listOf(feilOppgaveFeltverdi))
        val exception =
            assertThrows<IllegalArgumentException> {
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgaveSomManglerObligatoriskFelt), tx)
                }
            }

        assertEquals(
            "Oppgaven mangler obligatorisk felt opprettet",
            exception.message!!
        )
    }

    @Test
    fun `test at vi ikke logger aktørid`() {
        val område = oppgavemodellBuilder.område
        val oppgaveDto = oppgavemodellBuilder.lagOppgaveDtoMedManglendeVerdiIObligFelt()
        val oppgaveTypeDto = oppgavemodellBuilder.lagOppgavetypeDto()
        val feltdefinisjonDto = oppgavemodellBuilder.lagFeltdefinisjonDto()

        assertThrows<IllegalArgumentException> {
            OppgaveV3(
                oppgaveDto = oppgaveDto,
                oppgavetype = Oppgavetype(
                    dto = oppgaveTypeDto.oppgavetyper.first(),
                    definisjonskilde = "k9-sak-til-los",
                    område = område,
                    oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}?fakta=default&punkt=default",
                    feltdefinisjoner = Feltdefinisjoner(
                        feltdefinisjonerDto = feltdefinisjonDto,
                        område = område
                    ),
                    gyldigeFeltutledere = gyldigeFeltutledere
                )
            )
        }
    }
}