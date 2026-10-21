package no.nav.k9.los.nøkkeltall

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.infrastruktur.abac.cache.PepCache
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgavemottak.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavemottak.OppgaveV3Repository
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
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

        assertEquals(listOf("BEHANDLINGER", "DEL1", "DEL2"), kort.map { it.tittel.kode })
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
        assertEquals(listOf("BEHANDLINGER", "DEL2"), kode6Kort.map { it.tittel.kode })
        assertEquals(listOf(1L, 1L), kode6Kort.map { it.bunnlinje.verdi })
    }

    private fun lagreOppgave(
        område: Områder,
        type: String,
        status: Oppgavestatus,
        beskyttelse: PersonBeskyttelseType = PersonBeskyttelseType.UTEN_KODE6,
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
                felter = emptyList(),
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
