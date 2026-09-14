package no.nav.k9.los.lagretsok

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository

class LagretSøkTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val lagretSøkRepository: LagretSøkRepository,
    private val oppgaveQueryService: OppgaveQueryService,
) {
    fun hent(område: Områder, lagretSøkId: Long): LagretSøk {
        return lagretSøkRepository.hent(område, lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
    }

    suspend fun hentAlle(område: Områder, navIdent: String): List<LagretSøk> {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: return emptyList()
        return lagretSøkRepository.hentAlle(område, saksbehandler)
    }

    suspend fun nytt(navIdent: String, nyttLagretSøk: NyttLagretSøkRequest): Long {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = LagretSøk.nyttSøk(nyttLagretSøk, saksbehandler)
        return lagretSøkRepository.opprett(lagretSøk)
    }

    suspend fun endre(område: Områder, navIdent: String, endreLagretSøk: EndreLagretSøkRequest): LagretSøk {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(område, endreLagretSøk.id)
            ?: throw IllegalStateException("Lagret søk med id ${endreLagretSøk.id} finnes ikke")
        lagretSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endre(lagretSøk)
        return lagretSøk
    }

    suspend fun slett(område: Områder, navIdent: String, lagretSøkId: Long) {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(område, lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        lagretSøk.sjekkOmKanSlette(saksbehandler)
        lagretSøkRepository.slett(lagretSøk)
    }

    fun hentAntall(område: Områder, lagretSøkId: Long): Long {
        // Gjør ikke sjekk her på om lagret søk tilhører innlogget bruker, regner ikke det som nødvendig
        val lagretSøk = lagretSøkRepository.hent(område, lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        return oppgaveQueryService.queryForAntall(QueryRequest(område, lagretSøk.query))
    }

    suspend fun kopier(område: Områder, navIdent: String, lagretSøkId: Long, tittel: String): Long {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(område, lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        val nyttLagretSøk = lagretSøk.kopier(tittel, saksbehandler)
        return lagretSøkRepository.opprett(nyttLagretSøk)
    }
}