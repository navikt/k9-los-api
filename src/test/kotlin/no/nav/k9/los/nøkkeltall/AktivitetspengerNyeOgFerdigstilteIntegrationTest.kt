package no.nav.k9.los.nøkkeltall

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerOppgavetyper
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteService
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
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class AktivitetspengerNyeOgFerdigstilteIntegrationTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `saksbehandler får nye og ferdigstilte per dag og gruppe uten oppgaver fra andre områder`() {
        get<Områdesetup>().setup()
        get<no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.Områdesetup>().setup()
        val del1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
        val del2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode
        val klage = AktOppgavetypenavn.AKTIVITETSPENGERKLAGE.kode
        val feilutbetaling = "test-feilutbetaling"
        val definisjon = AktivitetspengerOppgavetyper.tilDto("http://localhost")
        val mal = definisjon.oppgavetyper.first()
        get<OppgavetypeTjeneste>().oppdater(definisjon.copy(
            oppgavetyper = definisjon.oppgavetyper + setOf(mal.copy(id = klage), mal.copy(id = feilutbetaling)),
        ))
        lagreOppgave(del1, BehandlingType.FØRSTEGANGSSØKNAD.kode, mottattDagerSiden = 0)
        lagreOppgave(del2, BehandlingType.REVURDERING.kode, mottattDagerSiden = 6, ferdigstiltDagerSiden = 0)
        lagreOppgave(del1, BehandlingType.FØRSTEGANGSSØKNAD.kode, mottattDagerSiden = 1, ferdigstiltDagerSiden = 1)
        lagreOppgave(klage, BehandlingType.KLAGE.kode, mottattDagerSiden = 2, ferdigstiltDagerSiden = 2)
        lagreOppgave(feilutbetaling, BehandlingType.TILBAKEKREVING.kode, mottattDagerSiden = 3, ferdigstiltDagerSiden = 3)
        lagreOppgave(klage, BehandlingType.ANKE.kode, mottattDagerSiden = 4, ferdigstiltDagerSiden = 4)
        lagreOppgave(feilutbetaling, BehandlingType.REVURDERING_TILBAKEKREVING.kode, mottattDagerSiden = 5, ferdigstiltDagerSiden = 5)
        lagreOppgave(del1, BehandlingType.FØRSTEGANGSSØKNAD.kode, mottattDagerSiden = 7, ferdigstiltDagerSiden = 7)
        lagreOppgave(del1, BehandlingType.FØRSTEGANGSSØKNAD.kode, mottattDagerSiden = -1)
        lagreOppgave("k9sak", BehandlingType.FØRSTEGANGSSØKNAD.kode, 0, 0, Områder.K9)

        val service = get<NyeOgFerdigstilteService>()
        runBlocking { service.oppdaterCache(Områder.AKTIVITETSPENGER, this) }
        val svar = service.hentCachetVerdi(Områder.AKTIVITETSPENGER, "ALLE")
        assertNotNull(svar.oppdatertTidspunkt)
        val iDag = LocalDate.now()
        assertEquals((6L downTo 0L).map { iDag.minusDays(it).format(DateTimeFormatter.ofPattern("dd.MM")) }, svar.kolonner)
        assertEquals(listOf("Nye", "Ferdigstilte"), svar.serier.map { it.navn })
        assertEquals(List(7) { 1 }, svar.serier[0].data)
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 1), svar.serier[1].data)

        val forventet = mapOf(
            "NAVKONTOR" to (listOf(0, 0, 0, 0, 0, 1, 1) to listOf(0, 0, 0, 0, 0, 1, 0)),
            "NAVFORVALTNING" to (listOf(1, 0, 0, 0, 0, 0, 0) to listOf(0, 0, 0, 0, 0, 0, 1)),
            "KLAGE" to (listOf(0, 0, 1, 0, 1, 0, 0) to listOf(0, 0, 1, 0, 1, 0, 0)),
            "FEILUTBETALING" to (listOf(0, 1, 0, 1, 0, 0, 0) to listOf(0, 1, 0, 1, 0, 0, 0)),
        )
        for ((gruppe, antall) in forventet) {
            val gruppesvar = service.hentCachetVerdi(Områder.AKTIVITETSPENGER, gruppe)
            assertEquals(antall.first, gruppesvar.serier[0].data)
            assertEquals(antall.second, gruppesvar.serier[1].data)
            assertEquals(svar.oppdatertTidspunkt, gruppesvar.oppdatertTidspunkt)
        }
        assertSame(svar, service.hentCachetVerdi(Områder.AKTIVITETSPENGER, "ALLE"))
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
                endretTidspunkt = LocalDate.now().minusDays(ferdigstiltDagerSiden ?: 0).atTime(15, 0),
                kildeområde = område,
                reservasjonsnøkkel = id,
                aktiv = true,
                felter = listOf(
                    felt("mottattDato", LocalDate.now().minusDays(mottattDagerSiden).atTime(13, 37).toString()),
                    felt("behandlingTypekode", behandlingstype),
                ),
            )
            get<OppgaveV3Repository>().nyOppgaveversjon(oppgave, tx)
            get<AktivOgPartisjonertOppgaveAjourholdTjeneste>().ajourholdOppgave(oppgave, 0, tx)
        }
    }
}
