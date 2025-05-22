package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository

class LagretSøkTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val lagretSøkRepository: LagretSøkRepository
) {
    suspend fun opprettLagretSøk(navIdent: String, opprettLagretSøk: OpprettLagretSøk): Long {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)
        return lagretSøkRepository.opprettLagretSøk(lagretSøk)
    }

    suspend fun endreLagretSøk(navIdent: String, endreLagretSøk: EndreLagretSøk): LagretSøk {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hentLagretSøk(endreLagretSøk.id)
            ?: throw IllegalStateException("Lagret søk med id ${endreLagretSøk.id} finnes ikke")
        lagretSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endreLagretSøk(lagretSøk)
        return lagretSøk
    }

    suspend fun slettLagretSøk(navIdent: String, lagretSøkId: Long) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hentLagretSøk(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        lagretSøk.sjekkOmKanSlette(saksbehandler)
        lagretSøkRepository.slettLagretSøk(lagretSøk)
    }
}