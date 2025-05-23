package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository

class LagretSøkTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val lagretSøkRepository: LagretSøkRepository
) {
    suspend fun hentAlle(navIdent: String): List<LagretSøk> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: return emptyList()
        return lagretSøkRepository.hentAlle(saksbehandler)
    }

    suspend fun opprett(navIdent: String, opprettLagretSøk: OpprettLagretSøk): Long {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)
        return lagretSøkRepository.opprett(lagretSøk)
    }

    suspend fun endre(navIdent: String, endreLagretSøk: EndreLagretSøk): LagretSøk {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(endreLagretSøk.id)
            ?: throw IllegalStateException("Lagret søk med id ${endreLagretSøk.id} finnes ikke")
        lagretSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endre(lagretSøk)
        return lagretSøk
    }

    suspend fun slett(navIdent: String, lagretSøkId: Long) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        lagretSøk.sjekkOmKanSlette(saksbehandler)
        lagretSøkRepository.slett(lagretSøk)
    }
}