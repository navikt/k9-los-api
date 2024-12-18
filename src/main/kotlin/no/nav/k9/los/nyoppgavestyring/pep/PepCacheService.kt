package no.nav.k9.los.nyoppgavestyring.pep

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
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
            runBlocking {
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
        return runBlocking {
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
            kildeområde = oppgave.kildeområde,
            kode6 = false,
            kode7 = false,
            egenAnsatt = false,
            oppdatert = LocalDateTime.now()
        )

        val saksnummer = oppgave.hentVerdi(oppgave.kildeområde, "saksnummer")
        return if (saksnummer != null) {
            pep.oppdater(saksnummer)
        } else {
            val aktører = hentAktører(oppgave)
            pep.oppdater(aktører)
        }
    }

    private suspend fun PepCache.oppdater(saksnummer: String): PepCache {
        return coroutineScope {
            val kode6Request = async(Span.current().asContextElement()) {
                pepClient.erSakKode6(fagsakNummer = saksnummer)
            }
            val kode7EllerEgenAnsattRequest = async(Span.current().asContextElement()) {
                pepClient.erSakKode7EllerEgenAnsatt(fagsakNummer = saksnummer)
            }
            val kode7EllerEgenAnsatt = kode7EllerEgenAnsattRequest.await()

            val oppdatertPepCache = oppdater(
                kode6 = kode6Request.await(),
                kode7 = kode7EllerEgenAnsatt,
                egenAnsatt = kode7EllerEgenAnsatt,
            )
            oppdatertPepCache
        }
    }

    private suspend fun PepCache.oppdater(aktører: List<AktørId>): PepCache {
        if (aktører.isEmpty()){
            return oppdater(kode6 = false, kode7 = false, egenAnsatt = false)
        }
        return coroutineScope {
            val kode6Request = aktører.map {
                async(Span.current().asContextElement()) {
                    pepClient.erAktørKode6(it.aktørId)
                }
            }
            val kode7EllerEgenAnsattRequest = aktører.map {
                async(Span.current().asContextElement()) {
                    pepClient.erAktørKode7EllerEgenAnsatt(it.aktørId)
                }
            }

            val minsteEnKode6 = kode6Request
                .map { it.await() }
                .reduce { a, b -> a || b }
            val minsteEnKode7EllerEgenAnsatt = kode7EllerEgenAnsattRequest
                .map { it.await() }
                .reduce { a, b -> a || b }
            oppdater(
                kode6 = minsteEnKode6,
                kode7 = minsteEnKode7EllerEgenAnsatt,
                egenAnsatt = minsteEnKode7EllerEgenAnsatt,
            )
        }
    }

    private fun hentAktører(oppgave: Oppgave): List<AktørId> {
        return listOfNotNull(
            oppgave.hentVerdi(oppgave.kildeområde, "aktorId"),
            oppgave.hentVerdi(oppgave.kildeområde, "pleietrengendeAktorId"),
            oppgave.hentVerdi(oppgave.kildeområde, "relatertPartAktorid")
        ).map { AktørId(it) }
    }

    private data class AktørId(val aktørId: String)

}

