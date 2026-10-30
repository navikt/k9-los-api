package no.nav.k9.los.nøkkeltall

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.oppgavedefinisjon.AktivitetspengerOppgavetyper
import no.nav.k9.los.infrastruktur.abac.cache.PepCache
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.oppgavemottak.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.OppgaveV3Repository
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.ung.kodeverk.behandling.BehandlingType
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class AktivitetspengerStatusFordelingIntegrationTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `statusfordeling og neddrilling avgrenser område del status og personbeskyttelse`() {
        get<Områdesetup>().setup()
        get<no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.Områdesetup>().setup()
        val del1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
        val del2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode
        lagreOppgave(Områder.AKTIVITETSPENGER, del1, Oppgavestatus.AAPEN)
        lagreOppgave(Områder.AKTIVITETSPENGER, del1, Oppgavestatus.VENTER)
        lagreOppgave(Områder.AKTIVITETSPENGER, del2, Oppgavestatus.UAVKLART)
        lagreOppgave(Områder.AKTIVITETSPENGER, del2, Oppgavestatus.LUKKET)
        lagreOppgave(Områder.AKTIVITETSPENGER, del2, Oppgavestatus.AAPEN, PersonBeskyttelseType.KODE6)
        lagreOppgave(Områder.K9, "k9sak", Oppgavestatus.AAPEN)

        val service = get<StatusFordelingService>()
        val kort = service.hentVerdi(Områder.AKTIVITETSPENGER, kode6 = false).tall

        assertEquals(listOf("BEHANDLINGER", "NAVKONTOR", "NAVFORVALTNING"), kort.map { it.tittel.kode })
        assertEquals(listOf(3L, 2L, 1L), kort.map { it.bunnlinje.verdi })
        assertEquals(listOf(1L, 1L, 0L), kort.map { it.topplinje.verdi })
        for (statuskort in kort) {
            for (linje in listOf(statuskort.topplinje, statuskort.bunnlinje) + statuskort.linjer) {
                assertEquals(
                    linje.verdi,
                    get<OppgaveQueryService>().queryForAntall(QueryRequest(Områder.AKTIVITETSPENGER, linje.kildespørring)),
                )
            }
        }

        val kode6Kort = service.hentVerdi(Områder.AKTIVITETSPENGER, kode6 = true).tall
        assertEquals(listOf("BEHANDLINGER", "NAVFORVALTNING"), kode6Kort.map { it.tittel.kode })
        assertEquals(listOf(1L, 1L), kode6Kort.map { it.bunnlinje.verdi })
    }

    @Test
    fun `klager og feilutbetalinger har egne kort og inngår i samlet antall`() {
        get<Områdesetup>().setup()
        val klage = AktOppgavetypenavn.AKTIVITETSPENGERKLAGE.kode
        val feilutbetaling = "test-feilutbetaling"
        // Testdefinisjoner inntil eventmottaket støtter disse oppgavetypene.
        val definisjon = AktivitetspengerOppgavetyper.tilDto("http://localhost")
        val mal = definisjon.oppgavetyper.first()
        get<OppgavetypeTjeneste>().oppdater(definisjon.copy(
            oppgavetyper = definisjon.oppgavetyper + setOf(mal.copy(id = klage), mal.copy(id = feilutbetaling)),
        ))
        lagreOppgave(Områder.AKTIVITETSPENGER, klage, Oppgavestatus.AAPEN, behandlingstype = BehandlingType.KLAGE.kode)
        lagreOppgave(Områder.AKTIVITETSPENGER, klage, Oppgavestatus.VENTER, behandlingstype = BehandlingType.ANKE.kode)
        lagreOppgave(Områder.AKTIVITETSPENGER, feilutbetaling, Oppgavestatus.UAVKLART, behandlingstype = BehandlingType.TILBAKEKREVING.kode)
        lagreOppgave(Områder.AKTIVITETSPENGER, feilutbetaling, Oppgavestatus.AAPEN, behandlingstype = BehandlingType.REVURDERING_TILBAKEKREVING.kode)
        lagreOppgave(Områder.AKTIVITETSPENGER, klage, Oppgavestatus.LUKKET, behandlingstype = BehandlingType.KLAGE.kode)
        lagreOppgave(Områder.AKTIVITETSPENGER, klage, Oppgavestatus.AAPEN, PersonBeskyttelseType.KODE6, BehandlingType.KLAGE.kode)
        lagreOppgave(Områder.AKTIVITETSPENGER, feilutbetaling, Oppgavestatus.VENTER, PersonBeskyttelseType.KODE6, BehandlingType.TILBAKEKREVING.kode)

        val service = get<StatusFordelingService>()
        val kort = service.hentVerdi(Områder.AKTIVITETSPENGER, kode6 = false).tall
        assertEquals(listOf("BEHANDLINGER", "KLAGE", "FEILUTBETALING"), kort.map { it.tittel.kode })
        assertEquals(listOf(4L, 2L, 2L), kort.map { it.bunnlinje.verdi })
        assertEquals(listOf(2L, 1L, 1L), kort.map { it.topplinje.verdi })
        for (statuskort in kort) {
            for (linje in listOf(statuskort.topplinje, statuskort.bunnlinje) + statuskort.linjer) {
                assertEquals(linje.verdi, get<OppgaveQueryService>().queryForAntall(
                    QueryRequest(Områder.AKTIVITETSPENGER, linje.kildespørring),
                ))
            }
        }
        val kode6Kort = service.hentVerdi(Områder.AKTIVITETSPENGER, kode6 = true).tall
        assertEquals(listOf("BEHANDLINGER", "KLAGE", "FEILUTBETALING"), kode6Kort.map { it.tittel.kode })
        assertEquals(listOf(2L, 1L, 1L), kode6Kort.map { it.bunnlinje.verdi })
    }

    private fun lagreOppgave(
        område: Områder,
        type: String,
        status: Oppgavestatus,
        beskyttelse: PersonBeskyttelseType = PersonBeskyttelseType.UTEN_KODE6,
        behandlingstype: String? = null,
    ) {
        get<TransactionalManager>().transaction { tx ->
            val områdeDefinisjon = get<OmrådeRepository>().hentOmråde(område, tx)
            val oppgavetype = get<OppgavetypeRepository>().hent(områdeDefinisjon, tx).oppgavetyper.single { it.eksternId == type }
            val id = UUID.randomUUID().toString()
            val oppgave = OppgaveV3(
                eksternId = id,
                eksternVersjon = "1",
                oppgavetype = oppgavetype,
                status = status,
                endretTidspunkt = LocalDateTime.now(),
                kildeområde = område,
                reservasjonsnøkkel = id,
                aktiv = true,
                felter = behandlingstype?.let { kode -> listOf(OppgaveFeltverdi(
                    null, oppgavetype.oppgavefelter.single { it.feltDefinisjon.eksternId == "behandlingTypekode" }, kode, null,
                )) } ?: emptyList(),
            )
            get<OppgaveV3Repository>().nyOppgaveversjon(oppgave, tx)
            get<AktivOgPartisjonertOppgaveAjourholdTjeneste>().ajourholdOppgave(oppgave, 0, tx)
            get<PepCacheRepository>().lagre(
                PepCache(id, område, beskyttelse == PersonBeskyttelseType.KODE6, false, LocalDateTime.now()),
                tx,
            )
        }
    }
}
