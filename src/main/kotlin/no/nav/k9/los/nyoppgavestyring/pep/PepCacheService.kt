package no.nav.k9.los.nyoppgavestyring.pep

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val transactionalManager: TransactionalManager,
) {

    suspend fun hentOgOppdaterVedBehov(tx: TransactionalSession, oppgave: Oppgave, maksimalAlder: Duration = Duration.ofMinutes(30)): PepCache {
        return pepCacheRepository.hent(kildeområde = oppgave.kildeområde, eksternId = oppgave.eksternId, tx).let { pepCache ->
            if (pepCache?.erGyldig(maksimalAlder) != true) {
                oppdater(tx, oppgave)
            } else {
                pepCache
            }
        }
    }

    fun oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet: Duration = Duration.ofHours(23)) {
        oppdaterCacheForOppgaverMedStatusEldreEnn(gyldighet, setOf(Oppgavestatus.VENTER, Oppgavestatus.AAPEN))
    }

    fun oppdaterCacheForLukkedeOppgaverEldreEnn(gyldighet: Duration = Duration.ofDays(30)) {
        oppdaterCacheForOppgaverMedStatusEldreEnn(gyldighet, setOf(Oppgavestatus.LUKKET))
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
            ?: return pep

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

            val (kode6, kode7EllerEgenAnsatt) = awaitAll(kode6Request, kode7EllerEgenAnsattRequest)
            val oppdatertPepCache = oppdater(
                kode6 = kode6,
                kode7 = kode7EllerEgenAnsatt,
                egenAnsatt = kode7EllerEgenAnsatt,
            )
            oppdatertPepCache
        }
    }
}

