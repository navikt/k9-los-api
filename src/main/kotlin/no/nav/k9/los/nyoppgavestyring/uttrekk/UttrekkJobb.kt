package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
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
    private var antallKjøringerUtenTreff = 0
    private val log = LoggerFactory.getLogger(UttrekkJobb::class.java)

    fun kjørUttrekk(uttrekkId: Long) {
        try {
            val uttrekk = uttrekkTjeneste.startUttrekk(uttrekkId)
            val lagretSøk = lagretSøkTjeneste.hent(uttrekk.lagretSøkId)
            val resultat = when (uttrekk.typeKjøring) {
                TypeKjøring.ANTALL ->
                    oppgaveQueryService.queryForAntall(QueryRequest(lagretSøk.query)).toString()

                TypeKjøring.OPPGAVER ->
                    LosObjectMapper.instance.writeValueAsString(oppgaveQueryService.query(QueryRequest(lagretSøk.query)))
            }
            uttrekkTjeneste.fullførUttrekk(uttrekkId, resultat)
        } catch (e: Exception) {
            uttrekkTjeneste.feilUttrekk(uttrekkId, e.message ?: "Ukjent feil under uttrekk")
        }
    }

    fun kjørAlleUttrekkSomIkkeHarKjørt() {
        val uttrekkListe = uttrekkTjeneste.hentAlle()
            .filter { it.status == UttrekkStatus.OPPRETTET }
        if (uttrekkListe.isEmpty()) {
            antallKjøringerUtenTreff++
            if (antallKjøringerUtenTreff % 10 == 0) {
                log.info("Sjekket om det er noen uttrekk å kjøre, men ingen funnet. Logger bare hvert tiende forsøk.")
            }
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