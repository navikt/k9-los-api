package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class UttrekkJobb(
    val oppgaveQueryService: OppgaveQueryService,
    val uttrekkTjeneste: UttrekkTjeneste,
) {
    private var antallKjøringerUtenTreff = 0
    private val log = LoggerFactory.getLogger(UttrekkJobb::class.java)

    fun kjørUttrekk(uttrekkId: Long) {
        try {
            val uttrekk = uttrekkTjeneste.startUttrekk(uttrekkId)

            when (uttrekk.typeKjøring) {
                TypeKjøring.ANTALL -> {
                    val antall = oppgaveQueryService.queryForAntall(
                        QueryRequest(uttrekk.query, queryTimeout = uttrekk.timeout)
                    )
                    uttrekkTjeneste.fullførUttrekk(uttrekkId, antall.toInt())
                }

                TypeKjøring.OPPGAVER -> {
                    val oppgaver = oppgaveQueryService.query(
                        QueryRequest(uttrekk.query, queryTimeout = uttrekk.timeout, avgrensning = uttrekk.avgrensning)
                    )
                    val resultatJson = LosObjectMapper.instance.writeValueAsString(oppgaver)
                    uttrekkTjeneste.fullførUttrekk(uttrekkId, oppgaver.size, resultatJson)
                }
            }
        } catch (e: Exception) {
            uttrekkTjeneste.feilUttrekk(uttrekkId, e.message)
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

    fun ryddOppUttrekk() {
        val uttrekkListe = uttrekkTjeneste.hentAlle()
            .filter { it.skalRyddesOpp() }
        if (uttrekkListe.isEmpty()) {
            log.info("Ingen uttrekk funnet som trenger opprydding")
            return
        }
        uttrekkListe.forEach {
            log.info("Markerer uttrekk med id ${it.id} som feilet på grunn av timeout")
            uttrekkTjeneste.feilUttrekk(it.id!!, "Uttrekk feilet på grunn av timeout")
        }
    }

}