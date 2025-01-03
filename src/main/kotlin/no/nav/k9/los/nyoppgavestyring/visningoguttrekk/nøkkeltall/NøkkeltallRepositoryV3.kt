package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.utils.Cache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

class NøkkeltallRepositoryV3 (private val dataSource: DataSource) {

    private val log: Logger = LoggerFactory.getLogger(NøkkeltallRepositoryV3::class.java)

    val cache = Cache<Boolean, List<GrupperteAksjonspunktVenteårsak>>(cacheSizeLimit = null)

    data class GrupperteAksjonspunktVenteårsak(
        val system: Fagsystem,
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val aksjonspunktKode: String,
        val frist: LocalDate,
        val venteårsak: String,
        val antall: Int
    )

    fun hentAllePåVentGruppert(): List<GrupperteAksjonspunktVenteårsak> {
        return cache.hent(false, Duration.ofMinutes(1)) { internalHentAllePåVentGruppert() }
    }

    fun internalHentAllePåVentGruppert(): List<GrupperteAksjonspunktVenteårsak> {
        //TODO ha en egen med/uten kode6 ?
        val oppgaver: List<GrupperteAksjonspunktVenteårsak> = using(sessionOf(dataSource)) {it.run(
                queryOf(
                    """
                       with oppgaver as (
                           select distinct on(oa.ekstern_id)
                               ot.ekstern_id as oppgavetype,
                               oav_ytelsetype.verdi as ytelsestype,
                               oav_behandlingtype.verdi as behandlingType,
                               oav_aksjonspunkt.verdi as aksjonspunktKode,
                               oav_venteårsak.verdi as venteårsak,
                               to_date(oav_ventefrist.verdi, 'YYYY-MM-DD') as ventefristDato
                           from oppgave_v3_aktiv oa
                           join oppgavetype ot on ot.id = oa.oppgavetype_id
                           left outer join oppgavefelt_verdi_aktiv oav_ytelsetype on oa.id = oav_ytelsetype.oppgave_id and oav_ytelsetype.feltdefinisjon_ekstern_id = 'ytelsestype'
                           left outer join oppgavefelt_verdi_aktiv oav_behandlingtype on oa.id = oav_behandlingtype.oppgave_id and oav_behandlingtype.feltdefinisjon_ekstern_id = 'behandlingTypekode'
                           left outer join oppgavefelt_verdi_aktiv oav_aksjonspunkt on oa.id = oav_aksjonspunkt.oppgave_id and oav_aksjonspunkt.feltdefinisjon_ekstern_id = 'aktivtAksjonspunkt'
                           left outer join oppgavefelt_verdi_aktiv oav_venteårsak on oa.id = oav_venteårsak.oppgave_id and oav_venteårsak.feltdefinisjon_ekstern_id = 'aktivVenteårsak'
                           left outer join oppgavefelt_verdi_aktiv oav_ventefrist on oa.id = oav_ventefrist.oppgave_id and oav_ventefrist.feltdefinisjon_ekstern_id = 'aktivVentefrist'
                           where oa.status = cast('VENTER' as oppgavestatus)
                           order by oa.ekstern_id, ventefristDato, ytelsestype, behandlingType, aksjonspunktKode, venteårsak
                       )
                       select oppgavetype, ytelsestype, behandlingType, aksjonspunktKode, venteårsak, ventefristDato, count(*) as antall
                       from oppgaver
                       group by 1, 2, 3, 4, 5, 6
         """.trimIndent()
                )
                    .map { row ->
                        GrupperteAksjonspunktVenteårsak(
                            system = mapOppgavetypeTilFagsystem(row.string("oppgavetype")),
                            fagsakYtelseType = FagsakYtelseType.fraKode(row.string("ytelsestype")),
                            behandlingType = BehandlingType.fraKode(row.string("behandlingType")),
                            aksjonspunktKode = row.string("aksjonspunktKode"),
                            frist = LocalDate.parse(row.string("ventefristDato"), DateTimeFormatter.ISO_LOCAL_DATE),
                            venteårsak = row.string("venteårsak"),
                            antall = row.int("antall")
                        )
                    }.asList
            )
        }
        log.info("Henter oppgaver på vent: " + oppgaver.stream().map { it.antall }.reduce(Int::plus).orElse(0) + " oppgaver")
        return oppgaver
    }

    fun mapOppgavetypeTilFagsystem(oppgavetypeEksternId: String): Fagsystem {
        return when (oppgavetypeEksternId){
            "k9sak" -> Fagsystem.K9SAK
            "k9klage" -> Fagsystem.K9KLAGE
            "k9punsj" -> Fagsystem.PUNSJ
            "k9tilbake" -> Fagsystem.K9TILBAKE
            else -> throw IllegalArgumentException("Oppgavetype " + oppgavetypeEksternId + " er ikke støttet her p.t.")
        }
    }
}