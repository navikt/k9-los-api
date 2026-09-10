package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.reservasjon.ManglerTilgangException
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
    fun leggTilSaksbehandlerForEpost(epost: String, område: Områder, skjermet: Boolean) {
        val eksisterende = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost)
        if (eksisterende == null) {
            saksbehandlerRepository.opprettSaksbehandler(epost, område, skjermet)
        } else {
            check(eksisterende.skjermet == skjermet) {
                "Saksbehandleren er registrert med en annen skjermingskategori"
            }
            saksbehandlerRepository.leggTilOmråde(eksisterende.id, område)
        }
    }

    fun slettSaksbehandlerForId(id: Long, bruker: BrukerkontekstMedOmråde) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedId(id)
            ?: throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
        slettFraOmråde(saksbehandler, bruker)
    }

    fun slettSaksbehandler(
        epost: String,
        bruker: BrukerkontekstMedOmråde,
    ) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, bruker.harTilgangTilKode6)
            ?: throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
        slettFraOmråde(saksbehandler, bruker)
    }

    private fun slettFraOmråde(saksbehandler: Saksbehandler, bruker: BrukerkontekstMedOmråde) {
        if (!bruker.erOppgavestyrer || bruker.område !in saksbehandler.områder ||
            saksbehandler.skjermet != bruker.harTilgangTilKode6
        ) {
            throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
        }
        transactionalManager.transaction { tx ->
            val låst = saksbehandlerRepository.hentForSletting(tx, saksbehandler.id)
                ?: throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
            if (bruker.område !in låst.områder || låst.skjermet != bruker.harTilgangTilKode6) {
                throw ManglerTilgangException("Saksbehandler er ikke tilgjengelig i valgt område")
            }
            oppgaveKøV3Repository.fjernSaksbehandlerFraOmråde(tx, låst.id, bruker.område)
            saksbehandlerRepository.slettFraOmråde(tx, låst, bruker.område)
        }
    }

    suspend fun hentSaksbehandlere(område: Områder, skjermet: Boolean): List<SaksbehandlerDto> {
        return transactionalManager.transactionSuspend { tx ->
            val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(tx, område, skjermet)
            val saksbehandlerIder = saksbehandlere.map { it.id!! }.toSet()
            val antallReservasjoner = reservasjonV3Tjeneste.tellReservasjonerForSaksbehandlere(saksbehandlerIder, område, tx)

            saksbehandlere.map {
                SaksbehandlerDto(
                    id = it.id,
                    brukerIdent = it.navident,
                    navn = it.navn,
                    epost = it.epost,
                    enhet = it.enhet,
                    antallAktiveReservasjoner = antallReservasjoner.getOrElse(it.id!!) { 0 }
                )
            }.sortedBy { it.navn }
        }
    }
}
