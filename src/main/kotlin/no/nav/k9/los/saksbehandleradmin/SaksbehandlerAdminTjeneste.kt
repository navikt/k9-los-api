package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.uttrekk.UttrekkTjeneste

class SaksbehandlerAdminTjeneste(
    private val pepClient: IPepClient,
    private val transactionalManager: TransactionalManager,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
    private val lagretSøkTjeneste: LagretSøkTjeneste,
    private val uttrekkTjeneste: UttrekkTjeneste,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {
    fun leggTilSaksbehandlerForEpost(område: Områder, kode6: Boolean, epost: String) {
        if (saksbehandlerRepository.finnSaksbehandlerMedEpost(epost) != null) {
            throw IllegalStateException("Saksbehandler finnes fra før")
        }
        saksbehandlerRepository.opprettSaksbehandler(område, kode6, epost)
    }

    suspend fun slettSaksbehandlerForId(område: Områder, id: Long) {
        val skjermet = pepClient.harTilgangTilKode6()

        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(id)

        val lagredeSøk = lagretSøkTjeneste.hentAlle(område, saksbehandler!!.navident!!)
        lagredeSøk.forEach {
            lagretSøkTjeneste.slett(område, saksbehandler.navident, it.id!!)
        }

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, saksbehandler.id, skjermet, true).forEach { kø ->
                oppgaveKøV3Repository.endre(tx, kø.copy(saksbehandlerIds = kø.saksbehandlerIds - saksbehandler.id), skjermet)
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandlerForId(tx, id, skjermet)
        }
    }

    suspend fun slettSaksbehandler(
        område: Områder,
        epost: String,
    ) {
        val skjermet = pepClient.harTilgangTilKode6()

        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost) ?: throw IllegalStateException("Kunne ikke finne saksbehandler med epost")
        if (saksbehandler.navident != null) {
            val lagredeSøk = lagretSøkTjeneste.hentAlle(område,saksbehandler.navident)
            lagredeSøk.forEach {
                lagretSøkTjeneste.slett(område, saksbehandler.navident, it.id!!)
            }
            val uttrekkeneTilSakbehandler = uttrekkTjeneste.hentForSaksbehandler(område, saksbehandler.id)
            uttrekkeneTilSakbehandler.forEach {
                uttrekkTjeneste.slettUtenTilgangssjekk(it.id!!)
            }
        }

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, saksbehandler.id, skjermet, true).forEach { kø ->
                oppgaveKøV3Repository.endre(tx, kø.copy(saksbehandlere = kø.saksbehandlere - epost), skjermet)
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandler(
                tx,
                epost,
                skjermet
            )
        }
    }

    suspend fun hentSaksbehandlere(område: Områder, kode6: Boolean): List<SaksbehandlerDto> {
        return transactionalManager.transactionSuspend { tx ->
            val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(
                område = område,
                skjermet = kode6,
                tx
            )
            val saksbehandlerIder = saksbehandlere.map { it.id }.toSet()
            val antallReservasjoner = reservasjonV3Tjeneste.tellReservasjonerForSaksbehandlere(saksbehandlerIder, tx)

            saksbehandlere.map {
                SaksbehandlerDto(
                    id = it.id,
                    brukerIdent = it.navident,
                    navn = it.navn,
                    epost = it.epost,
                    enhet = it.enhet,
                    antallAktiveReservasjoner = antallReservasjoner.getOrElse(it.id) { 0 }
                )
            }.sortedBy { it.navn }
        }
    }
}