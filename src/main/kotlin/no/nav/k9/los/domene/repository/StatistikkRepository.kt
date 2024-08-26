package no.nav.k9.los.domene.repository

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.FerdigstiltBehandling
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.saksbehandler.oppgave.BehandletOppgave
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import no.nav.k9.los.utils.LosObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class StatistikkRepository(
    private val dataSource: DataSource
) {
    companion object {
        val SISTE_8_UKER_I_DAGER = 55
    }

    fun lagreBehandling(brukerIdent: String, oppgave : BehandletOppgave) {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        using(sessionOf(dataSource)) {
            it.transaction { tx ->

                val json = LosObjectMapper.instance.writeValueAsString(oppgave)
                tx.run(
                    queryOf(
                        """
                    insert into siste_behandlinger as k (id, data)
                    values (:id, :dataInitial :: jsonb)
                    on conflict (id) do update
                    set data = jsonb_set(k.data, '{siste_behandlinger,999999}', :data :: jsonb, true)
                 """, mapOf("id" to brukerIdent, "dataInitial" to "{\"siste_behandlinger\": [$json]}", "data" to json)
                    ).asUpdate
                )
            }
        }
    }

    fun hentBehandlinger(ident: String): List<BehandletOppgave> {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        val json = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                        select data, timestamp from (
                            select distinct on (saksnummer) 
                                (data -> 'saksnummer') as saksnummer, 
                                (data -> 'timestamp') as timestamp, 
                                data from (select jsonb_array_elements(data -> 'siste_behandlinger') as data from siste_behandlinger where id = :id) as saker 
                                order by saksnummer, timestamp desc 
                             ) as s 
                         order by timestamp desc
                         limit 10
                    """.trimIndent(),
                    mapOf("id" to ident)
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        return json.map { LosObjectMapper.instance.readValue(it, BehandletOppgave::class.java) }
    }

    fun lagreFerdigstilt(bt: String, eksternId: UUID, dato: LocalDate) {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                //language=PostgreSQL
                tx.run(
                    queryOf(
                        """insert into ferdigstilte_behandlinger as k (behandlingType, dato, data)
                                    values (:behandlingType, :dato, :dataInitial ::jsonb)
                                    on conflict (behandlingType, dato) do update
                                    set data = k.data || :data ::jsonb
                                 """, mapOf(
                            "behandlingType" to bt,
                            "dataInitial" to "[\"${eksternId}\"]",
                            "data" to "[\"$eksternId\"]",
                            "dato" to dato,
                        )
                    ).asUpdate
                )
            }
        }
    }

    fun lagre(
        alleOppgaverNyeOgFerdigstilte: AlleOppgaverNyeOgFerdigstilte,
        f: (AlleOppgaverNyeOgFerdigstilte) -> AlleOppgaverNyeOgFerdigstilte
    ) {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select * from nye_og_ferdigstilte where behandlingType = :behandlingType and fagsakYtelseType = :fagsakYtelseType and dato = :dato for update",
                        mapOf(
                            "behandlingType" to alleOppgaverNyeOgFerdigstilte.behandlingType.kode,
                            "fagsakYtelseType" to alleOppgaverNyeOgFerdigstilte.fagsakYtelseType.kode,
                            "dato" to alleOppgaverNyeOgFerdigstilte.dato
                        )
                    )
                        .map { row ->
                            AlleOppgaverNyeOgFerdigstilte(
                                behandlingType = BehandlingType.fraKode(row.string("behandlingType")),
                                fagsakYtelseType = FagsakYtelseType.fraKode(row.string("fagsakYtelseType")),
                                dato = row.localDate("dato"),
                                ferdigstilte = LosObjectMapper.instance.readValue(
                                    row.stringOrNull("ferdigstilte") ?: "[]"
                                ),
                                ferdigstilteSaksbehandler = LosObjectMapper.instance.readValue(
                                    row.stringOrNull("ferdigstiltesaksbehandler") ?: "[]"
                                ),
                                nye = LosObjectMapper.instance.readValue(row.stringOrNull("nye") ?: "[]")
                            )
                        }.asSingle
                )
                val alleOppgaverNyeOgFerdigstilteSomPersisteres = if (run != null) {
                    f(run)
                } else {
                    f(alleOppgaverNyeOgFerdigstilte)
                }

                tx.run(
                    queryOf(
                        """
                                    insert into nye_og_ferdigstilte as k (behandlingType, fagsakYtelseType, dato, nye, ferdigstilte, ferdigstiltesaksbehandler)
                                    values (:behandlingType, :fagsakYtelseType, :dato, :nye ::jsonb, :ferdigstilte ::jsonb, :ferdigstiltesaksbehandler ::jsonb)
                                    on conflict (behandlingType, fagsakYtelseType, dato) do update
                                    set nye = :nye ::jsonb , ferdigstilte = :ferdigstilte ::jsonb , ferdigstiltesaksbehandler = :ferdigstiltesaksbehandler ::jsonb
                     """, mapOf(
                            "behandlingType" to alleOppgaverNyeOgFerdigstilteSomPersisteres.behandlingType.kode,
                            "fagsakYtelseType" to alleOppgaverNyeOgFerdigstilteSomPersisteres.fagsakYtelseType.kode,
                            "dato" to alleOppgaverNyeOgFerdigstilteSomPersisteres.dato,
                            "nye" to LosObjectMapper.instance.writeValueAsString(
                                alleOppgaverNyeOgFerdigstilteSomPersisteres.nye
                            ),
                            "ferdigstilte" to LosObjectMapper.instance.writeValueAsString(
                                alleOppgaverNyeOgFerdigstilteSomPersisteres.ferdigstilte
                            ),
                            "ferdigstiltesaksbehandler" to LosObjectMapper.instance.writeValueAsString(
                                alleOppgaverNyeOgFerdigstilteSomPersisteres.ferdigstilteSaksbehandler
                            )
                        )
                    ).asUpdate
                )

            }
        }
    }

    private val hentFerdigstilteOgNyeHistorikkMedYtelsetypeCache = Cache<String, List<AlleOppgaverNyeOgFerdigstilte>>(cacheSizeLimit = 1000)
    fun hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker(
        refresh: Boolean = false
    ): List<AlleOppgaverNyeOgFerdigstilte> {
        if (!refresh) {
            val cacheObject = hentFerdigstilteOgNyeHistorikkMedYtelsetypeCache.get("default")
            if (cacheObject != null) {
                return cacheObject.value
            }
        }

        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }.increment()

        val ferdigstilteOgNyeOppgavehistorikk = hentFerdigstilteOgNyeHistorikkPerAntallDager(SISTE_8_UKER_I_DAGER)
        val datoMap = ferdigstilteOgNyeOppgavehistorikk.groupBy { it.dato }
        val ret = mutableListOf<AlleOppgaverNyeOgFerdigstilte>()
        for (i in 55 downTo 1) {
            val dato = LocalDate.now().minusDays(i.toLong())
            val defaultList = mutableListOf<AlleOppgaverNyeOgFerdigstilte>()
            for (behandlingType in BehandlingType.values()) {
                defaultList.addAll(tomListe(behandlingType, dato))
            }
            val dagensStatistikk = datoMap.getOrDefault(dato, defaultList)
            val behandlingsTypeMap = dagensStatistikk.groupBy { it.behandlingType }

            for (behandlingstype in BehandlingType.values()) {

                val perBehandlingstype =
                    behandlingsTypeMap.getOrDefault(behandlingstype, tomListe(behandlingstype, dato))
                val fagSakytelsesMap = perBehandlingstype.groupBy { it.fagsakYtelseType }
                for (fagsakYtelseType in FagsakYtelseType.values()) {
                    ret.addAll(
                        fagSakytelsesMap.getOrDefault(
                            fagsakYtelseType, listOf(
                                AlleOppgaverNyeOgFerdigstilte(
                                    fagsakYtelseType = fagsakYtelseType,
                                    behandlingType = behandlingstype,
                                    dato = dato
                                )
                            )
                        )
                    )
                }
            }
        }
        hentFerdigstilteOgNyeHistorikkMedYtelsetypeCache.set(
            "default",
            CacheObject(ret, LocalDateTime.now().plusMinutes(60))
        )
        return ret
    }

    fun hentFerdigstilteOgNyeHistorikkPerAntallDager(antall: Int = SISTE_8_UKER_I_DAGER): List<AlleOppgaverNyeOgFerdigstilte> {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }.increment()

        return using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """
                            select behandlingtype, fagsakYtelseType, dato, ferdigstilte, nye, ferdigstiltesaksbehandler
                            from nye_og_ferdigstilte  where dato >= current_date - :antall::interval
                    """.trimIndent(),
                    mapOf("antall" to "\'${antall} days\'")
                )
                    .map { row ->
                        AlleOppgaverNyeOgFerdigstilte(
                            behandlingType = BehandlingType.fraKode(row.string("behandlingType")),
                            fagsakYtelseType = FagsakYtelseType.fraKode(row.string("fagsakYtelseType")),
                            dato = row.localDate("dato"),
                            ferdigstilte = LosObjectMapper.instance.readValue(row.stringOrNull("ferdigstilte") ?: "[]"),
                            nye = LosObjectMapper.instance.readValue(row.stringOrNull("nye") ?: "[]"),
                            ferdigstilteSaksbehandler = LosObjectMapper.instance.readValue(
                                row.stringOrNull("ferdigstiltesaksbehandler") ?: "[]"
                            ),
                        )
                    }.asList
            )
        }
    }

    fun hentFerdigstiltOppgavehistorikk(antallDagerHistorikk: Int = SISTE_8_UKER_I_DAGER): List<FerdigstiltBehandling> {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }.increment()

        val startDato = LocalDate.now().minusDays(antallDagerHistorikk.toLong())

        val list = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    SELECT date(oppgave_v2.ferdigstilt_tidspunkt) AS dato, 
                    behandling.fagsystem as fagsystem_type, 
                    behandling.ytelse_type AS ytelse_type,
                    behandling.behandling_type as behandling_type,
                    oppgave_v2.ferdigstilt_enhet AS behandlende_enhet,
                    oppgave_v2.ferdigstilt_saksbehandler AS ansvarlig_saksbehandler
                    FROM oppgave_v2 LEFT JOIN behandling ON behandling.id = oppgave_v2.behandling_id
                    WHERE oppgave_v2.ferdigstilt_tidspunkt > :start_dato ORDER BY dato
                    """.trimIndent(),
                    mapOf("start_dato" to startDato)
                ).map { rad ->
                    FerdigstiltBehandling(
                        dato = rad.localDate("dato"),
                        fagsystemType = rad.stringOrNull("fagsystem_type"),
                        fagsakYtelseType = rad.stringOrNull("ytelse_type"),
                        behandlingType = rad.stringOrNull("behandling_type"),
                        behandlendeEnhet = rad.stringOrNull("behandlende_enhet"),
                        saksbehandler = rad.stringOrNull("ansvarlig_saksbehandler")
                    )
                }.asList
            )
        }
        return list
    }

    private fun tomListe(
        behandlingstype: BehandlingType,
        dato: LocalDate
    ): MutableList<AlleOppgaverNyeOgFerdigstilte> {
        val defaultList = mutableListOf<AlleOppgaverNyeOgFerdigstilte>()
        for (fagsakYtelseType in FagsakYtelseType.values()) {
            defaultList.add(
                AlleOppgaverNyeOgFerdigstilte(
                    fagsakYtelseType = fagsakYtelseType,
                    behandlingType = behandlingstype,
                    dato = dato
                )
            )
        }
        return defaultList
    }

}
