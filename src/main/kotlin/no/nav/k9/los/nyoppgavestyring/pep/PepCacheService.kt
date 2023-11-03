package no.nav.k9.los.nyoppgavestyring.pep

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import java.time.Duration
import java.time.LocalDateTime

class PepCacheService(
    private val pepClient: IPepClient,
    private val pepCacheRepository: PepCacheRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val transactionalManager: TransactionalManager,
) {

    fun erOppgaveKode6(oppgave: Oppgave): Boolean {
        return transactionalManager.transaction { tx ->
            runBlocking { hentOgOppdaterVedBehov(tx, oppgave).kode6 }
        }
    }

    fun hentOgOppdaterVedBehov(
        kildeområde: String,
        eksternId: String,
        maksimalAlder: Duration = Duration.ofMinutes(30)
    ): PepCache {
        return transactionalManager.transaction { tx ->
            val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternId(
                tx,
                kildeområde = kildeområde,
                eksternId = eksternId
            )
            runBlocking {
                hentOgOppdaterVedBehov(tx, oppgave, maksimalAlder)
            }
        }
    }

    suspend fun hentOgOppdaterVedBehov(tx: TransactionalSession, oppgave: Oppgave, maksimalAlder: Duration = Duration.ofMinutes(30)): PepCache {
        return pepCacheRepository.hent(kildeområde = oppgave.kildeområde, eksternId = oppgave.eksternId, tx).let { pepCache ->
            if (pepCache?.erGyldig(maksimalAlder) != true) {
                pepCache ?: oppdater(tx, oppgave)
            } else {
                pepCache
            }
        }
    }

    fun oppdaterCacheForOppgaverEldreEnn(gyldighet: Duration = Duration.ofHours(23)) {
        transactionalManager.transaction { tx ->
            runBlocking {
                val oppgaverSomMåOppdateres =
                    oppgaveRepository.hentÅpneOgVentendeOppgaverMedPepCacheEldreEnn(LocalDateTime.now() - gyldighet, antall = 100, tx)
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
            kode6 = true,
            kode7 = true,
            egenAnsatt = true,
            oppdatert = LocalDateTime.now()
        )

        val saksnummer = oppgave.hentVerdi(oppgave.kildeområde, "saksnummer")
            ?: throw IllegalStateException("Kan ikke gjøre oppslag uten saksnummer ${oppgave.eksternId}")

        return pep.oppdater(saksnummer)
    }

    private suspend fun PepCache.oppdater(saksnummer: String): PepCache {
        return coroutineScope {
            val kode6Request = async {
                pepClient.erSakKode6(fagsakNummer = saksnummer)
            }
            val kode7EllerEgenAnsattRequest = async {
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
}

