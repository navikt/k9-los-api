package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository

class SaksbehandlerAdminTjeneste(
    private val pepClient: IPepClient,
    private val transactionalManager: TransactionalManager,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
) {

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    suspend fun søkSaksbehandler(epostDto: EpostDto): Saksbehandler {
        var saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epostDto.epost)
        if (saksbehandler == null) {
            saksbehandler = Saksbehandler(
                null, null, null, epostDto.epost, mutableSetOf(), null
            )
            saksbehandlerRepository.addSaksbehandler(saksbehandler)
        }
        return saksbehandler
    }

    suspend fun leggTilSaksbehandler(epost: String) {
        if (saksbehandlerRepository.finnSaksbehandlerMedEpost(epost) != null) {
            throw IllegalStateException("Saksbehandler finnes fra før")
        }
        // lagrer med tomme verdier, disse blir populert etter at saksbehandleren har logget seg inn
        val saksbehandler = Saksbehandler(null, null, null, epost, mutableSetOf(), null)
        saksbehandlerRepository.addSaksbehandler(saksbehandler)
    }

    suspend fun slettSaksbehandler(
        epost: String,
    ) {
        val skjermet = pepClient.harTilgangTilKode6()

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, epost, skjermet, true).forEach { kø ->
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

    suspend fun hentSaksbehandlere(): List<SaksbehandlerDto> {
        val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()
        return saksbehandlere.map {
            SaksbehandlerDto(
                id = it.id,
                brukerIdent = it.brukerIdent,
                navn = it.navn,
                epost = it.epost,
                enhet = it.enhet,
                oppgavekoer = emptyList())
        }.sortedBy { it.navn }
    }
}