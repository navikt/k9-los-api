package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.uttrekk.UttrekkTjeneste

class SaksbehandlerAdminTjeneste(
    private val transactionalManager: TransactionalManager,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
    private val lagretSøkTjeneste: LagretSøkTjeneste,
    private val uttrekkTjeneste: UttrekkTjeneste,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {
    fun leggTilSaksbehandlerForEpost(område: Områder, kode6: Boolean, epost: String) {
        val eksisterende = saksbehandlerRepository.finnSaksbehandlerMedEpostBådeKode6OgVanlig(epost)
        if (eksisterende == null) {
            saksbehandlerRepository.opprettSaksbehandler(område, kode6, epost)
        } else {
            check(eksisterende.skjermet == kode6) {
                "Saksbehandleren er registrert med en annen skjermingskategori"
            }
            saksbehandlerRepository.leggTilOmråde(eksisterende.id, område)
        }
    }

    fun slettSaksbehandlerForId(område: Områder, kode6: Boolean, id: Long) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(id)

        val lagredeSøk = lagretSøkTjeneste.hentAlle(område, saksbehandler!!.navident!!, kode6)
        lagredeSøk.forEach {
            lagretSøkTjeneste.slett(område, saksbehandler.navident, kode6, it.id!!)
        }

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(
                område = område,
                skjermet = kode6,
                saksbehandlerId = saksbehandler.id,
                medSaksbehandlere = true,
                tx = tx
            ).forEach { kø ->
                oppgaveKøV3Repository.endre(
                    område,
                    kode6,
                    kø.copy(saksbehandlerIds = kø.saksbehandlerIds - saksbehandler.id),
                    tx
                )
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandlerForId(tx, id, kode6)
        }
    }

    fun slettSaksbehandler(
        område: Områder,
        kode6: Boolean,
        epost: String,
    ) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, kode6)
            ?: throw IllegalStateException("Kunne ikke finne saksbehandler med epost")
        if (saksbehandler.navident != null) {
            val lagredeSøk = lagretSøkTjeneste.hentAlle(område, saksbehandler.navident, kode6)
            lagredeSøk.forEach {
                lagretSøkTjeneste.slett(område, saksbehandler.navident, kode6, it.id!!)
            }
            val uttrekkeneTilSakbehandler = uttrekkTjeneste.hentForSaksbehandler(område, saksbehandler.id)
            uttrekkeneTilSakbehandler.forEach {
                uttrekkTjeneste.slettUtenTilgangssjekk(it.id!!)
            }
        }

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(område, kode6,saksbehandler.id, true, tx).forEach { kø ->
                oppgaveKøV3Repository.endre(område, kode6, kø.copy(saksbehandlere = kø.saksbehandlere - epost), tx)
            }

            // Sletter fra tabellene saksbehandler_omrade og saksbehandler
            saksbehandlerRepository.slettSaksbehandler(
                område,
                kode6,
                epost,
                tx,
            )
        }
    }

    suspend fun hentSaksbehandlere(område: Områder, kode6: Boolean): List<SaksbehandlerDto> {
        return transactionalManager.transactionSuspend { tx ->
            val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(
                område = område,
                skjermet = kode6,
                tx = tx
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
