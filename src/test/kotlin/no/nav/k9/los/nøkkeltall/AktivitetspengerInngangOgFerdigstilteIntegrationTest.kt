package no.nav.k9.los.nøkkeltall

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerOppgaver
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte.InngangOgFerdigstilteService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.oppgavemottak.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.OppgaveV3Repository
import no.nav.ung.kodeverk.behandling.BehandlingType
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AktivitetspengerInngangOgFerdigstilteIntegrationTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `inngang og ferdigstilte avgrenser område periode Nav-enhet og behandlingstype`() {
        get<Områdesetup>().setup()
        get<no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.Områdesetup>().setup()
        val del1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
        val del2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode
        val førstegang = BehandlingType.FØRSTEGANGSSØKNAD.kode
        val revurdering = BehandlingType.REVURDERING.kode
        lagreOppgave(del1, førstegang, mottattDagerSiden = 0)
        lagreOppgave(del2, revurdering, mottattDagerSiden = 7, ferdigstiltDagerSiden = 0)
        lagreOppgave(del1, revurdering, mottattDagerSiden = 14, ferdigstiltDagerSiden = 7)
        lagreOppgave(del2, førstegang, mottattDagerSiden = 28, ferdigstiltDagerSiden = 14)
        lagreOppgave(del2, førstegang, mottattDagerSiden = 29, ferdigstiltDagerSiden = 28)
        lagreOppgave(del1, førstegang, mottattDagerSiden = 40, ferdigstiltDagerSiden = 29)
        lagreOppgave(del2, revurdering, mottattDagerSiden = 29)
        lagreOppgave("k9sak", førstegang, mottattDagerSiden = 0, ferdigstiltDagerSiden = 0, område = Områder.K9)

        val service = get<InngangOgFerdigstilteService>()
        runBlocking { service.oppdaterCache(Områder.AKTIVITETSPENGER, this) }
        val verdi = service.hentCachetVerdi(Områder.AKTIVITETSPENGER)
        val totalt = verdi.tall.single { it.hovedgruppe == "ALLE" && it.undergruppe == "TOTALT" }

        assertEquals(listOf(1L, 2L, 3L, 4L), totalt.serier.values.map { it.first.hovedtall.verdi })
        assertEquals(listOf(1L, 2L, 3L, 4L), totalt.serier.values.map { it.second.hovedtall.verdi })
        val førstegangIdag = verdi.tall.single { it.hovedgruppe == "ALLE" && it.undergruppe == "FØRSTEGANG" }.serier.getValue("idag")
        assertEquals(1L, førstegangIdag.first.hovedtall.verdi)
        assertEquals(0L, førstegangIdag.second.hovedtall.verdi)
        val forventetPerGruppe = mapOf(
            "NAVKONTOR" to (listOf(1L, 1L, 2L, 2L) to listOf(0L, 1L, 1L, 1L)),
            "NAVFORVALTNING" to (listOf(0L, 1L, 1L, 2L) to listOf(1L, 1L, 2L, 3L)),
        )
        for ((gruppe, antall) in forventetPerGruppe) {
            val rad = verdi.tall.single { it.hovedgruppe == gruppe && it.undergruppe == "TOTALT" }
            assertEquals(antall.first, rad.serier.values.map { it.first.hovedtall.verdi })
            assertEquals(antall.second, rad.serier.values.map { it.second.hovedtall.verdi })
        }
        assertTrue(verdi.tall.all { rad -> rad.serier.values.all { it.first.linjer.isEmpty() && it.second.linjer.isEmpty() } })
        for ((periode, kort) in totalt.serier) {
            val delkort = verdi.tall.filter { it.hovedgruppe != "ALLE" && it.undergruppe == "TOTALT" }.map { it.serier.getValue(periode) }
            assertEquals(kort.first.hovedtall.verdi, delkort.sumOf { it.first.hovedtall.verdi })
            assertEquals(kort.second.hovedtall.verdi, delkort.sumOf { it.second.hovedtall.verdi })
        }
    }

    @Test
    fun `klager og feilutbetalinger telles utenfor ordinære oppgavetyper i alle perioder`() {
        get<Områdesetup>().setup()
        val klage = AktOppgavetypenavn.AKTIVITETSPENGERKLAGE.kode
        val feilutbetaling = "test-feilutbetaling"
        // Testdefinisjoner inntil eventmottaket støtter disse oppgavetypene.
        val definisjon = AktivitetspengerOppgaver.lagOppgaveDefinisjon("http://localhost")
        val mal = definisjon.oppgavetyper.first()
        get<OppgavetypeTjeneste>().oppdater(definisjon.copy(
            oppgavetyper = definisjon.oppgavetyper + setOf(mal.copy(id = klage), mal.copy(id = feilutbetaling)),
        ))
        lagreOppgave(klage, BehandlingType.KLAGE.kode, mottattDagerSiden = 0, ferdigstiltDagerSiden = 0)
        lagreOppgave(klage, BehandlingType.ANKE.kode, mottattDagerSiden = 7, ferdigstiltDagerSiden = 7)
        lagreOppgave(feilutbetaling, BehandlingType.TILBAKEKREVING.kode, mottattDagerSiden = 14, ferdigstiltDagerSiden = 14)
        lagreOppgave(feilutbetaling, BehandlingType.REVURDERING_TILBAKEKREVING.kode, mottattDagerSiden = 28, ferdigstiltDagerSiden = 28)
        lagreOppgave(feilutbetaling, BehandlingType.TILBAKEKREVING.kode, mottattDagerSiden = 29, ferdigstiltDagerSiden = 29)

        val service = get<InngangOgFerdigstilteService>()
        runBlocking { service.oppdaterCache(Områder.AKTIVITETSPENGER, this) }
        val verdi = service.hentCachetVerdi(Områder.AKTIVITETSPENGER)
        val forventedeAntall = mapOf(
            "TOTALT" to listOf(1L, 2L, 3L, 4L),
            "KLAGE" to listOf(1L, 2L, 2L, 2L),
            "FEILUTBETALING" to listOf(0L, 0L, 1L, 2L),
        )
        for ((undergruppe, antall) in forventedeAntall) {
            val rad = verdi.tall.single { it.hovedgruppe == "ALLE" && it.undergruppe == undergruppe }
            assertEquals(antall, rad.serier.values.map { it.first.hovedtall.verdi })
            assertEquals(antall, rad.serier.values.map { it.second.hovedtall.verdi })
        }
        assertTrue(verdi.tall.filter { it.hovedgruppe != "ALLE" }.all { rad ->
            rad.serier.values.all { it.first.hovedtall.verdi == 0L && it.second.hovedtall.verdi == 0L }
        })
    }

    private fun lagreOppgave(
        type: String,
        behandlingstype: String,
        mottattDagerSiden: Long,
        ferdigstiltDagerSiden: Long? = null,
        område: Områder = Områder.AKTIVITETSPENGER,
    ) {
        get<TransactionalManager>().transaction { tx ->
            val områdeDefinisjon = get<OmrådeRepository>().hentOmråde(område, tx)
            val oppgavetype = get<OppgavetypeRepository>().hent(områdeDefinisjon, tx).oppgavetyper.single { it.eksternId == type }
            fun felt(kode: String, verdi: String) = OppgaveFeltverdi(
                null, oppgavetype.oppgavefelter.single { it.feltDefinisjon.eksternId == kode }, verdi, null,
            )
            val id = UUID.randomUUID().toString()
            val oppgave = OppgaveV3(
                eksternId = id,
                eksternVersjon = "1",
                oppgavetype = oppgavetype,
                status = if (ferdigstiltDagerSiden == null) Oppgavestatus.AAPEN else Oppgavestatus.LUKKET,
                endretTidspunkt = LocalDate.now().minusDays(ferdigstiltDagerSiden ?: 0).atStartOfDay(),
                kildeområde = område,
                reservasjonsnøkkel = id,
                aktiv = true,
                felter = listOf(
                    felt("mottattDato", LocalDate.now().minusDays(mottattDagerSiden).atStartOfDay().toString()),
                    felt("behandlingTypekode", behandlingstype),
                ),
            )
            get<OppgaveV3Repository>().nyOppgaveversjon(oppgave, tx)
            get<AktivOgPartisjonertOppgaveAjourholdTjeneste>().ajourholdOppgave(oppgave, 0, tx)
        }
    }
}
