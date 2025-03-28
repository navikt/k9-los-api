package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.db.TransactionalManager
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get

import java.time.LocalDateTime

class OppgaveV3TjenesteTest : AbstractK9LosIntegrationTest() {
    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        OppgaveTestDataBuilder()
        oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()
        transactionalManager = get<TransactionalManager>()
    }

    @Test
    fun oppdaterEksisterendeOppgaveversjon() {
        val oppgaveVersjon1 = lagOppgaveDto(
            eksternId = "test123",
            versjon = 0,
            oppgavestatus = Oppgavestatus.AAPEN,
            aksjonspunkt = "5015",
        )

        val oppgaveVersjon2 = lagOppgaveDto(
            eksternId = "test123",
            versjon = 1,
            oppgavestatus = Oppgavestatus.LUKKET,
            aksjonspunkt = "5016",
        )

        val oppgaveVersjon1korrigert = lagOppgaveDto(
            eksternId = "test123",
            versjon = 0,
            oppgavestatus = Oppgavestatus.AAPEN,
            aksjonspunkt = "9001",
        )

        val oppgaveVersjon2korrigert = lagOppgaveDto(
            eksternId = "test123",
            versjon = 1,
            oppgavestatus = Oppgavestatus.AAPEN,
            aksjonspunkt = "5015",
        )

        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveVersjon1, tx)
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveVersjon2, tx)

            val oppgave1korrigert = oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveVersjon1korrigert, 0, tx)
            oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgave1korrigert, 0, tx)

            val oppgave2korrigert = oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveVersjon2korrigert, 1, tx)
            oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgave2korrigert, 1, tx)
        }

        val vasketOppgave1 = transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.hentOppgaveversjon("K9", oppgaveVersjon1.id, oppgaveVersjon1.versjon, tx)
        }

        val vasketOppgave2 = transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.hentOppgaveversjon("K9", oppgaveVersjon2.id, oppgaveVersjon2.versjon, tx)
        }

        assertThat(vasketOppgave1.aktiv).isFalse()
        vasketOppgave1.hentOppgavefeltverdi("aksjonspunkt")!!.let { aksjonspunkt ->
            assertThat(aksjonspunkt.verdi).isEqualTo("9001")
        }

        assertThat(vasketOppgave2.aktiv).isTrue()
        vasketOppgave2.hentOppgavefeltverdi("aksjonspunkt")!!.let { aksjonspunkt ->
            assertThat(aksjonspunkt.verdi).isEqualTo("5015")
        }
    }

    private fun lagOppgaveDto(eksternId: String, versjon: Int, oppgavestatus: Oppgavestatus, aksjonspunkt: String?): no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto {
        return OppgaveDto(
            id = eksternId,
            versjon = versjon.toString(),
            område = Område(eksternId = "K9").eksternId,
            kildeområde = "k9-sak-til-los",
            type = "k9sak",
            status = oppgavestatus.kode,
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "K9_b_${FagsakYtelseType.FRISINN}_273857",
            feltverdier = listOfNotNull(
                aksjonspunkt?.let {
                    OppgaveFeltverdiDto(
                        nøkkel = "aksjonspunkt",
                        verdi = aksjonspunkt
                    )
                },
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "utenlandstilsnitt",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "resultattype",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "behandlingsstatus",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "behandlingTypekode",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "totrinnskontroll",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "ytelsestype",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "saksnummer",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "hastesak",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "behandlingUuid",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "fagsystem",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerAnnet",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerAnnetIkkeSaksbehandlingstid",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerArbeidsgiver",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "helautomatiskBehandlet",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerTekniskFeil",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSøker",
                    verdi = "false"
                ),
            )
        )
    }
}