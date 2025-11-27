package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest

class UttrekkJobb(
    val oppgaveQueryService: OppgaveQueryService,
    val uttrekkTjeneste: UttrekkTjeneste,
    val lagretSøkTjeneste: LagretSøkTjeneste
) {
    suspend fun kjørUttrekk(uttrekkId: Long) {
        val uttrekk = uttrekkTjeneste.startUttrekk(uttrekkId)
        val lagretSøk = lagretSøkTjeneste.hent(uttrekk.lagretSøkId)
        try {
            val resultat = oppgaveQueryService.queryToFile(QueryRequest(lagretSøk.query))
            uttrekkTjeneste.fullførUttrekk(uttrekkId, resultat)
        } catch (e: Exception) {
            uttrekkTjeneste.feilUttrekk(uttrekkId, e.message ?: "Ukjent feil under uttrekk")
        }
    }

}