package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class UttrekkJobb(
    val oppgaveQueryService: OppgaveQueryService,
    val uttrekkTjeneste: UttrekkTjeneste,
    val lagretSøkTjeneste: LagretSøkTjeneste
) {
    private val log = LoggerFactory.getLogger(UttrekkJobb::class.java)

    suspend fun kjørUttrekk(uttrekkId: Long) {
        val uttrekk = uttrekkTjeneste.startUttrekk(uttrekkId)
        val lagretSøk = lagretSøkTjeneste.hent(uttrekk.lagretSøkId)
        try {
            val resultat = when (uttrekk.typeKjøring) {
                TypeKjøring.ANTALL ->
                    oppgaveQueryService.queryForAntall(QueryRequest(lagretSøk.query)).toString()
                TypeKjøring.OPPGAVER ->
                    oppgaveQueryService.queryToFile(QueryRequest(lagretSøk.query))
            }
            uttrekkTjeneste.fullførUttrekk(uttrekkId, resultat)
        } catch (e: Exception) {
            uttrekkTjeneste.feilUttrekk(uttrekkId, e.message ?: "Ukjent feil under uttrekk")
        }
    }

    suspend fun kjørAlleUttrekkSomIkkeHarKjørt() {
        val uttrekkListe = uttrekkTjeneste.hentAlle()
            .filter { it.status == UttrekkStatus.OPPRETTET }
        if (uttrekkListe.isEmpty()) {
            log.info("Sjekket om det er noen uttrekk å kjøre, men ingen funnet")
            return
        }
        log.info("Starter kjøring av ${uttrekkListe.size} uttrekk")
        val tidsbruk = measureTime {
            for (uttrekk in uttrekkListe) {
                kjørUttrekk(uttrekk.id!!)
            }
        }
        log.info("Ferdig med kjøring av ${uttrekkListe.size} uttrekk på $tidsbruk")
    }

}