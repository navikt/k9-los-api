package no.nav.k9.los.infrastruktur.abac.cache

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.*
import kotliquery.TransactionalSession
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.time.Duration
import java.time.LocalDateTime
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class PepCacheService(
    private val pepClient: IPepClient,
    private val pepCacheRepository: PepCacheRepository,
    private val transactionalManager: TransactionalManager
) {

    @WithSpan
    fun oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet: Duration = Duration.ofHours(23)) {
        oppdaterCacheForOppgaverMedStatusEldreEnn(gyldighet, setOf(Oppgavestatus.VENTER, Oppgavestatus.AAPEN))
    }

    private fun oppdaterCacheForOppgaverMedStatusEldreEnn(
        gyldighet: Duration = Duration.ofHours(23),
        status: Set<Oppgavestatus>
    ) {
        transactionalManager.transaction { tx ->
            runBlocking(Dispatchers.IO) {
                val oppgaverSomMåOppdateres = pepCacheRepository.hentOppgaverMedStatusOgPepCacheEldreEnn(
                    tidspunkt = LocalDateTime.now() - gyldighet,
                    antall = 1,
                    status = status,
                    tx
                )
                oppgaverSomMåOppdateres.forEach { oppgave -> oppdater(tx, oppgave) }
            }
        }
    }

    suspend fun oppdater(tx: TransactionalSession, oppgave: PepCacheInput): PepCache {
        return lagPepCacheFra(oppgave).also { nyPepCache -> pepCacheRepository.lagre(nyPepCache, tx) }
    }

    private suspend fun lagPepCacheFra(oppgaveIdOgAktører: PepCacheInput): PepCache {
        // PepCacheInput kommer i dag utelukkende fra K9-oppgaver, jf. spørringen i
        // PepCacheRepository.hentOppgaverMedStatusOgPepCacheEldreEnn som filtrerer på kildeomrade
        // = 'K9'. Når UNG-oppgaver skal pep-caches må området følge med fra oppgaven.
        val område = Områder.K9
        val pep = PepCache(
            eksternId = oppgaveIdOgAktører.eksternId,
            kildeområde = område.eksternId,
            kode6 = false,
            kode7 = false,
            egenAnsatt = false,
            oppdatert = LocalDateTime.now(),
            område = område
        )

        return if (oppgaveIdOgAktører.saksnummer != null) {
            pep.oppdater(oppgaveIdOgAktører.saksnummer, område)
        } else {
            pep.oppdater(oppgaveIdOgAktører.aktører, område)
        }
    }

    private suspend fun PepCache.oppdater(saksnummer: String, område: Områder): PepCache {
        val diskresjonskoder = pepClient.diskresjonskoderForSak(saksnummer, område)

        //TODO ikke sette kode7 og egenansatt til samme verdi, det er misvisende ifht modellen som finnes. Det fungerer funksjonelt p.t fordi kode7 og egen ansatt (skjermet) håndteres samlet i køene
        val kode7ellerEgenAnsatt =
            diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
        return oppdater(
            kode6 = diskresjonskoder.contains(Diskresjonskode.KODE6),
            kode7 = kode7ellerEgenAnsatt,
            egenAnsatt = kode7ellerEgenAnsatt,
        )
    }

    private suspend fun PepCache.oppdater(aktører: List<String>, område: Områder): PepCache {
        if (aktører.isEmpty()) {
            return oppdater(kode6 = false, kode7 = false, egenAnsatt = false)
        }
        return coroutineScope {
            val requests = aktører.map {
                async(Span.current().asContextElement()) {
                    pepClient.diskresjonskoderForPerson(it, område)
                }
            }

            val diskresjonskoder = requests
                .awaitAll()
                .flatten()

            //TODO ikke sette kode7 og egenansatt til samme verdi, det er misvisende ifht modellen som finnes. Det fungerer funksjonelt p.t fordi kode7 og egen ansatt (skjermet) håndteres samlet i køene
            val kode7ellerEgenAnsatt =
                diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
            oppdater(
                kode6 = diskresjonskoder.contains(Diskresjonskode.KODE6),
                kode7 = kode7ellerEgenAnsatt,
                egenAnsatt = kode7ellerEgenAnsatt,
            )
        }
    }
}

data class PepCacheInput(
    val eksternId: String,
    val saksnummer: String?,
    val aktører: List<String>
)