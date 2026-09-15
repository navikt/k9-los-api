package no.nav.k9.los.lagretsok

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository

class LagretSøkTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val lagretSøkRepository: LagretSøkRepository,
    private val oppgaveQueryService: OppgaveQueryService,
) {
    fun hent(område: Områder, navIdent: String, lagretSøkId: Long): LagretSøk? {
        return lagretSøkRepository.hent(område, navIdent, lagretSøkId)
    }

    suspend fun hentAlle(område: Områder, navIdent: String): List<LagretSøk> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: return emptyList()
        return lagretSøkRepository.hentAlle(område, saksbehandler)
    }

    fun hentAntall(område: Områder, navIdent: String, lagretSøkId: Long): Long? {
        val lagretSøk = lagretSøkRepository.hent(område, navIdent, lagretSøkId) ?: return null
        return oppgaveQueryService.queryForAntall(QueryRequest(område, lagretSøk.query))
    }

    suspend fun nytt(område: Områder, navIdent: String, nyttLagretSøk: NyttLagretSøkRequest): Long {
        val saksbehandler = saksbehandler(navIdent)
        return lagretSøkRepository.opprett(LagretSøk.nyttSøk(område, saksbehandler, nyttLagretSøk))
    }

    suspend fun endre(område: Områder, navIdent: String, endreLagretSøk: EndreLagretSøkRequest): LagretSøk {
        val saksbehandler = saksbehandler(navIdent)
        val lagretSøk = hentEllerKast(område, navIdent, endreLagretSøk.id)
        lagretSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endre(lagretSøk)
        return lagretSøk
    }

    suspend fun kopier(område: Områder, navIdent: String, lagretSøkId: Long, tittel: String): Long {
        val saksbehandler = saksbehandler(navIdent)
        val lagretSøk = hentEllerKast(område, navIdent, lagretSøkId)
        return lagretSøkRepository.opprett(lagretSøk.kopier(tittel, saksbehandler))
    }

    suspend fun slett(område: Områder, navIdent: String, lagretSøkId: Long) {
        val saksbehandler = saksbehandler(navIdent)
        val lagretSøk = hentEllerKast(område, navIdent, lagretSøkId)
        lagretSøk.sjekkOmKanSlette(saksbehandler)
        lagretSøkRepository.slett(lagretSøk)
    }

    private suspend fun saksbehandler(navIdent: String): Saksbehandler =
        saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")

    private fun hentEllerKast(område: Områder, navIdent: String, lagretSøkId: Long): LagretSøk =
        lagretSøkRepository.hent(område, navIdent, lagretSøkId)
            ?: throw IllegalArgumentException("Lagret søk med id $lagretSøkId finnes ikke")
}
