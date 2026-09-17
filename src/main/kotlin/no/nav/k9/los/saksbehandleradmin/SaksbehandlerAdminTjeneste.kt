package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.reservasjon.ManglerTilgangException
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste

class SaksbehandlerAdminTjeneste(
    private val transactionalManager: TransactionalManager,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste
) {
    fun leggTilSaksbehandlerForEpost(område: Områder, kode6: Boolean, epost: String) {
        val eksisterende = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, kode6)
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
            ?: throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
        slettFraOmråde(område, kode6, saksbehandler)
    }

    fun slettSaksbehandler(
        område: Områder,
        kode6: Boolean,
        epost: String,
    ) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, kode6)
            ?: throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
        slettFraOmråde(område, kode6, saksbehandler)
    }

    private fun slettFraOmråde(område: Områder, kode6: Boolean, saksbehandler: Saksbehandler) {
        if (saksbehandler.skjermet != kode6) {
            throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
        }
        transactionalManager.transaction { tx ->
            val låst = saksbehandlerRepository.hentForSletting(tx, saksbehandler.id)
                ?: throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
            if (låst.skjermet != kode6) {
                throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
            }
            oppgaveKøV3Repository.fjernSaksbehandlerFraOmråde(tx, låst.id, område)
            saksbehandlerRepository.slettFraOmråde(tx, låst, område)
        }
    }

    suspend fun hentSaksbehandlere(område: Områder, kode6: Boolean): List<SaksbehandlerDto> {
        return transactionalManager.transactionSuspend { tx ->
            val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(område, kode6, tx)
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
