package no.nav.k9.los.domene.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.tjenester.innsikt.Databasekall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class NøkkeltallRepository(private val dataSource: DataSource) {

    private val log: Logger = LoggerFactory.getLogger(NøkkeltallRepository::class.java)
    data class GrupperteAksjonspunktVenteårsak(
        val system : Fagsystem,
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val aksjonspunktKode: String,
        val frist: LocalDate,
        val venteårsak: String,
        val antall: Int
    )

    fun hentAllePåVentGruppert(): List<GrupperteAksjonspunktVenteårsak> {
        val t0 = System.currentTimeMillis()
        val oppgaver: List<GrupperteAksjonspunktVenteårsak> = using(sessionOf(dataSource)) {
            it.run(
                //følgende og henter ut aktive (status OPPR) autopunkter (alle autopunkter og bare autopunkter har frist satt) for en oppgave. Det skal kun kunne være en slik pr behandling/oppgave
                //   jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)')
                //   @?? er @?-operatoren med escaping
                queryOf(
                    """
             select 
                    data ->> 'system' as system,
                    data -> 'fagsakYtelseType' ->> 'kode' as fagsakYtelseType,
                    data -> 'behandlingType' ->> 'kode' as behandlingType,
                    jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)') ->> 'aksjonspunktKode' as aksjonspunktKode,
                    jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)') ->> 'venteårsak' as venteårsak,
                    substring(jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)') ->> 'frist', 1, 10) as fristDato,
                    count(*) as antall
             from oppgave
             where data -> 'aksjonspunkter' -> 'apTilstander' @?? '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)'
             group by 1, 2, 3, 4, 5, 6
         """.trimIndent()
                )
                    .map { row ->
                        GrupperteAksjonspunktVenteårsak(
                            system = Fagsystem.fraKode(row.string("system")),
                            fagsakYtelseType =  FagsakYtelseType.fraKode(row.string("fagsakYtelseType")),
                            behandlingType =  BehandlingType.fraKode(row.string("behandlingType")),
                            aksjonspunktKode = row.string("aksjonspunktKode"),
                            frist = LocalDate.parse(row.string("fristDato"), DateTimeFormatter.ISO_LOCAL_DATE),
                            venteårsak = row.string("venteårsak"),
                            antall = row.int("antall")
                        )
                    }.asList
            )
        }
        val spørringTidsforbrukMs = System.currentTimeMillis() - t0

        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        log.info("Henter oppgaver på vent: " + oppgaver.stream().map{it.antall}.reduce(Int::plus).orElse(0) + " oppgaver" + " spørring: " + spørringTidsforbrukMs)
        return oppgaver
    }
}