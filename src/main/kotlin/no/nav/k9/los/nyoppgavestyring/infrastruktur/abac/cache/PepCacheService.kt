package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.time.Duration
import java.time.LocalDateTime

class PepCacheService(
    private val pepClient: IPepClient,
    private val pepCacheRepository: PepCacheRepository,
    private val oppgaveRepository: OppgaveRepository,
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
                val oppgaverSomMåOppdateres = oppgaveRepository.hentOppgaverMedStatusOgPepCacheEldreEnn(
                    tidspunkt = LocalDateTime.now() - gyldighet,
                    antall = 1,
                    status = status,
                    tx
                )
                oppgaverSomMåOppdateres.forEach { oppgave -> oppdater(tx, oppgave) }
            }
        }
    }

    fun oppdater(tx: TransactionalSession, kildeområde: String, eksternId: String): PepCache {
        return runBlocking(Dispatchers.IO) {
            val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternId(
                tx,
                kildeområde = kildeområde,
                eksternId = eksternId
            )
            oppdater(tx, oppgave)
        }
    }

    suspend fun oppdater(tx: TransactionalSession, oppgave: Oppgave): PepCache {
        return lagPepCacheFra(oppgave).also { nyPepCache -> pepCacheRepository.lagre(nyPepCache, tx) }
    }

    private suspend fun lagPepCacheFra(oppgave: Oppgave): PepCache {
        val pep = PepCache(
            eksternId = oppgave.eksternId,
            kildeområde = "K9",
            kode6 = false,
            kode7 = false,
            egenAnsatt = false,
            oppdatert = LocalDateTime.now()
        )

        val saksnummer = oppgave.hentVerdi("saksnummer")
        return if (saksnummer != null) {
            pep.oppdater(saksnummer)
        } else {
            val aktører = hentAktører(oppgave)
            pep.oppdater(aktører)
        }
    }

    private suspend fun PepCache.oppdater(saksnummer: String): PepCache {
        val diskresjonskoder = pepClient.diskresjonskoderForSak(saksnummer)

        //TODO ikke sette kode7 og egenansatt til samme verdi, det er misvisende ifht modellen som finnes. Det fungerer funksjonelt p.t fordi kode7 og egen ansatt (skjermet) håndteres samlet i køene
        val kode7ellerEgenAnsatt = diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
        return oppdater(
                kode6 = diskresjonskoder.contains(Diskresjonskode.KODE6),
                kode7 = kode7ellerEgenAnsatt,
                egenAnsatt = kode7ellerEgenAnsatt,
        )
    }

    private suspend fun PepCache.oppdater(aktører: List<AktørId>): PepCache {
        if (aktører.isEmpty()){
            return oppdater(kode6 = false, kode7 = false, egenAnsatt = false)
        }
        return coroutineScope {
            val requests = aktører.map {
                async(Span.current().asContextElement()) {
                    pepClient.diskresjonskoderForPerson(it.aktørId)
                }
            }

            val diskresjonskoder = requests
                .map { it.await() }
                .reduce { a, b -> a + b }

            //TODO ikke sette kode7 og egenansatt til samme verdi, det er misvisende ifht modellen som finnes. Det fungerer funksjonelt p.t fordi kode7 og egen ansatt (skjermet) håndteres samlet i køene
            val kode7ellerEgenAnsatt = diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
            oppdater(
                kode6 = diskresjonskoder.contains(Diskresjonskode.KODE6),
                kode7 = kode7ellerEgenAnsatt,
                egenAnsatt = kode7ellerEgenAnsatt,
            )
        }
    }

    private fun hentAktører(oppgave: Oppgave): List<AktørId> {
        return listOfNotNull(
            oppgave.hentVerdi("aktorId"),
            oppgave.hentVerdi("pleietrengendeAktorId"),
            oppgave.hentVerdi("relatertPartAktorid")
        ).map { AktørId(it) }
    }

    private data class AktørId(val aktørId: String)

}

