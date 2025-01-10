package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.utils.Cache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.sql.DataSource

class OppgaverGruppertRepository(private val dataSource: DataSource) {
    private val log: Logger = LoggerFactory.getLogger(OppgaverGruppertRepository::class.java)

    private val cacheAntallÅpneOppgaverPrOppgavetypeBehandlingstype =
        Cache<Boolean, List<BehandlingstypeAntallDto>>(cacheSizeLimit = null)

    data class BehandlingstypeAntallDto(
        val behandlistype: String?,
        val antall: Int
    )

    fun hentTotaltAntallÅpneOppgaver(kode6 : Boolean): Int {
        return hentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(kode6)
            .sumOf { it.antall }
    }

    fun hentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(kode6 : Boolean): List<BehandlingstypeAntallDto> {
        return cacheAntallÅpneOppgaverPrOppgavetypeBehandlingstype.hent(
            nøkkel = kode6,
            Duration.ofMinutes(1)
        ) { doHentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(kode6) }
    }

    private fun doHentAntallÅpneOppgaverPrOppgavetypeBehandlingstype(kode6: Boolean): List<BehandlingstypeAntallDto> {
        val resultat = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    /***
                     * kan også gruppere på oppgavetype (k9sak/punsj/k9klage/k9tilbake) ved å legge til
                     *  (select ekstern_id from oppgavetype ot where ot.id = oa.oppgavetype_id) as oppgavetype, og ta den som grupperingsnøkkel 2
                     */
                    """
                    with oppgaver as (
                          select
                              (select verdi from oppgavefelt_verdi_aktiv ova where ova.oppgave_id = oa.id and ova.feltdefinisjon_ekstern_id = 'behandlingTypekode') as behandlingType
                          from oppgave_v3_aktiv oa
                          join oppgave_pep_cache opc on opc.ekstern_id = oa.ekstern_id
                          where oa.status = cast('AAPEN' as oppgavestatus) and opc.kode6 = :kode6)
                    select behandlingType, count(*) as antall
                    from oppgaver
                    group by behandlingType;
                """.trimIndent(),
                    mapOf("kode6" to kode6)
                )
                    .map {
                        BehandlingstypeAntallDto(
                            it.stringOrNull("behandlingType"),
                            it.int("antall")
                        )
                    }
                    .asList
            )
        }
        if (resultat.any {it.behandlistype == null} ){
            log.warn("Fant ${resultat.filter {it.behandlistype == null}.sumOf { it.antall }} oppgaver uten behandlingstype, de blir ikke med oversikt som viser antall")
            return resultat.filter { it.behandlistype != null }
        } else {
            return resultat
        }
    }
}