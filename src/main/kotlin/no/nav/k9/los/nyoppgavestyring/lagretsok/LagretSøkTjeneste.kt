package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository

class LagretSøkTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val lagretSøkRepository: LagretSøkRepository,
    private val oppgaveQueryService: OppgaveQueryService,
) {
    fun hent(lagretSøkId: Long): LagretSøk {
        return lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
    }

    suspend fun hentAlle(navIdent: String): List<LagretSøk> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: return emptyList()
        return lagretSøkRepository.hentAlle(saksbehandler)
    }

    suspend fun opprett(navIdent: String, kode6: Boolean, opprettLagretSøk: OpprettLagretSøk): Long {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler, kode6)
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

    fun hentAntall(lagretSøkId: Long): Long {
        // Gjør ikke sjekk her på om lagret søk tilhører innlogget bruker, regner ikke det som nødvendig
        val lagretSøk = lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        return oppgaveQueryService.queryForAntall(QueryRequest(lagretSøk.query))
    }

    suspend fun kopier(navIdent: String, lagretSøkId: Long, søk: OpprettLagretSøk) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        val nyttLagretSøk = lagretSøk.kopier(søk, saksbehandler)
        lagretSøkRepository.opprett(nyttLagretSøk)
    }
}