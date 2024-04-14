package no.nav.k9.los.domene.repository

import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.aksjonspunktbehandling.objectMapper
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleApneBehandlinger
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverDto
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.mock.AksjonspunktMock
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource
import kotlin.collections.LinkedHashMap


class OppgaveRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient,
    private val refreshOppgave: Channel<UUID>
) {
    private val log: Logger = LoggerFactory.getLogger(OppgaveRepository::class.java)

    data class GrupperteAksjonspunktVenteårsak(
        val system : Fagsystem,
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val aksjonspunktKode: String,
        val frist: LocalDate,
        val venteårsak: String,
        val antall: Int
    )

    fun hentK9SakPåVentGruppert(): List<GrupperteAksjonspunktVenteårsak> {
        val t0 = System.currentTimeMillis()
        val oppgaver: List<GrupperteAksjonspunktVenteårsak> = using(sessionOf(dataSource)) {
            it.run(
        queryOf(
         //FIXME? skal denne også sjekke på aktiv? gjorde ikke det i forrige implementasjon - i så fall også oppdater indeks
         //TODO kan vurdere å fjerne uthenting og gruppering på aksjonspunktkode, beholder inntil videre for evt annen gjenbruk
         //følgende og henter ut aktive (status OPPR) autopunkter (alle autopunkter og bare autopunkter har frist satt) for en oppgave. Det skal kun kunne være en slik pr behandling/oppgave
         //   jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)')
         """
             select 
                    data -> 'system' as system,
                    data -> 'fagsakYtelseType' ->> 'kode' as fagsakYtelseType,
                    data -> 'behandlingType' ->> 'kode' as behandlingType,
                    jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)') ->> 'aksjonspunktKode' as aksjonspunktKode,
                    jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)') ->> 'venteårsak' as venteårsak,
                    substring(jsonb_path_query(data -> 'aksjonspunkter' -> 'apTilstander', '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)') ->> 'frist', 1, 10) as fristDato,
                    count(*) as antall
             from oppgave
             where data -> 'aksjonspunkter' -> 'apTilstander' @? '${'$'}[*] ? (@."status"=="OPPR" && @."frist" != null)'
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

    fun hent(uuid: UUID): Oppgave {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from oppgave where id = :id",
                    mapOf("id" to uuid.toString())
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        }
        return try {
            objectMapper().readValue(json!!, Oppgave::class.java)
        } catch (e: NullPointerException) {
            log.error("feiler for denne json $json")
            throw e
        }
    }

    fun hentOppgaverSomMatcher(pleietrengendeAktørId: String, fagsakYtelseType: FagsakYtelseType): List<OppgaveMedId> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf("""
                    select id, data from oppgave where data ->> 'aktiv' = 'true'
                    and data -> 'fagsakYtelseType' ->> 'kode' = ?
                    and data ->> 'pleietrengendeAktørId' = ?
                    """,
                    fagsakYtelseType.kode,
                    pleietrengendeAktørId
                )
                    .map { row ->
                        OppgaveMedId(
                            UUID.fromString(row.string("id")),
                            objectMapper().readValue(row.string("data"), Oppgave::class.java)
                        )
                    }.asList
            )
        }
    }

    fun hentOppgaverSomMatcherSaksnummer(saksnummer: String): List<OppgaveMedId> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf("""
                    select id, data from oppgave where data ->> 'fagsakSaksnummer' = ?
                    """,
                    saksnummer
                )
                    .map { row ->
                        OppgaveMedId(
                            UUID.fromString(row.string("id")),
                            objectMapper().readValue(row.string("data"), Oppgave::class.java)
                        )
                    }.asList
            )
        }
    }

    fun hentHvis(uuid: UUID): Oppgave? {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        val json: String? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from oppgave where id = :id",
                    mapOf("id" to uuid.toString())
                )
                    .map { row ->
                        row.string("data")
                    }.asSingle
            )
        }
        return if (json != null) {
            objectMapper().readValue(json, Oppgave::class.java)
        } else null
    }

    suspend fun hentHasteoppgaver(): List<Oppgave> {
        val kode6 = pepClient.harTilgangTilKode6()
        val json: List<String> = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """
                    SELECT o.data
                    FROM merknad m INNER JOIN Oppgave o ON (m.ekstern_referanse = o.id)
                        LEFT JOIN behandling b on b.ekstern_referanse = o.id
                    WHERE m.slettet = false AND b.ferdigstilt_tidspunkt is NULL
                    ORDER BY m.opprettet DESC
                    """
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        val oppgaver = json.map { s -> objectMapper().readValue(s, Oppgave::class.java) }
            .filter { it.kode6 == kode6 }

        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        return oppgaver
    }

    fun lagre(uuid: UUID, f: (Oppgave?) -> Oppgave) {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                val run = tx.run(
                    queryOf(
                        "select data from oppgave where id = :id for update",
                        mapOf("id" to uuid.toString())
                    )
                        .map { row ->
                            row.string("data")
                        }.asSingle
                )

                val oppgave = if (!run.isNullOrEmpty()) {
                    f(objectMapper().readValue(run, Oppgave::class.java))
                } else {
                    f(null)
                }
                runBlocking {
                    oppgave.kode6 = sjekkKode6(oppgave)
                    oppgave.skjermet = sjekkKode7EllerEgenAnsatt(oppgave)
                }
                val json = objectMapper().writeValueAsString(oppgave)
                tx.run(
                    queryOf(
                        """
                    insert into oppgave as k (id, data)
                    values (:id, :data :: jsonb)
                    on conflict (id) do update
                    set data = :data :: jsonb
                 """, mapOf("id" to uuid.toString(), "data" to json)
                    ).asUpdate
                )
            }
        }
    }

    private suspend fun sjekkKode6(oppgave: Oppgave): Boolean {
        if (oppgave.fagsakSaksnummer.isNotBlank()) {
            val søker = pepClient.erSakKode6(oppgave.fagsakSaksnummer)
            val pleietrengende =
                if (oppgave.pleietrengendeAktørId != null) pepClient.erAktørKode6(oppgave.pleietrengendeAktørId) else false
            val relatertPart =
                if (oppgave.relatertPartAktørId != null) pepClient.erAktørKode6(oppgave.relatertPartAktørId) else false
            return (søker || pleietrengende || relatertPart)

        }
        // oppgaver laget av punsj har ikke fagsakSaksnummer
        val søker = pepClient.erAktørKode6(oppgave.aktorId)
        val pleietrengende =
            if (oppgave.pleietrengendeAktørId != null) pepClient.erAktørKode6(oppgave.pleietrengendeAktørId) else false
        return (søker || pleietrengende)
    }

    private suspend fun sjekkKode7EllerEgenAnsatt(oppgave: Oppgave): Boolean {
        if (oppgave.fagsakSaksnummer.isNotBlank()) {
            val søker = pepClient.erSakKode7EllerEgenAnsatt(oppgave.fagsakSaksnummer)
            val pleietrengende =
                if (oppgave.pleietrengendeAktørId != null) pepClient.erAktørKode7EllerEgenAnsatt(oppgave.pleietrengendeAktørId) else false
            val relatertPart =
                if (oppgave.relatertPartAktørId != null) pepClient.erAktørKode7EllerEgenAnsatt(oppgave.relatertPartAktørId) else false
            return (søker || pleietrengende || relatertPart)
        }
        // oppgaver laget av punsj har ikke fagsakSaksnummer
        val søker = pepClient.erAktørKode7EllerEgenAnsatt(oppgave.aktorId)
        val pleietrengende =
            if (oppgave.pleietrengendeAktørId != null) pepClient.erAktørKode7EllerEgenAnsatt(oppgave.pleietrengendeAktørId) else false
        return (søker || pleietrengende)
    }

    fun hentOppgaver(oppgaveider: Collection<UUID>): List<Oppgave> {

        val oppgaveiderList = oppgaveider.toList()
        if (oppgaveider.isEmpty()) {
            return emptyList()
        }

        val session = sessionOf(dataSource)
        val json: List<String> = using(session) {

            //language=PostgreSQL
            it.run(
                queryOf(
                    "select data from oppgave " +
                            "where id in (${
                                IntRange(0, oppgaveiderList.size - 1).joinToString { t -> ":p$t" }
                            })",
                    IntRange(
                        0,
                        oppgaveiderList.size - 1
                    ).associate { t -> "p$t" to oppgaveiderList[t].toString() as Any }
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return json.filter { it.indexOf("oppgaver") == -1 }
            .map { s -> objectMapper().readValue(s, Oppgave::class.java) }
            .toList()
            .sortedBy { oppgave -> oppgave.behandlingOpprettet }
    }

    fun hentPleietrengendeAktør(oppgaveider: Collection<UUID>): Map<String, String> {
        val oppgaveiderList = oppgaveider.toList()
        if (oppgaveider.isEmpty()) {
            return emptyMap()
        }

        val session = sessionOf(dataSource)
        val res: List<Pair<String, String>> = using(session) {

            //language=PostgreSQL
            it.run(
                queryOf(
                    "select id, data->'pleietrengendeAktørId' as aktor from oppgave " +
                            "where id in (${
                                IntRange(0, oppgaveiderList.size - 1).joinToString { t -> ":p$t" }
                            }) and data->>'pleietrengendeAktørId' is not null",
                    IntRange(
                        0,
                        oppgaveiderList.size - 1
                    ).associate { t -> "p$t" to oppgaveiderList[t].toString() as Any }
                )
                    .map { row ->
                        Pair(row.string("id"), row.string("aktor"))
                    }.asList
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        val mapOf = mutableMapOf<String, String>()

        res.forEach {
            mapOf[it.second] = it.first
        }
        return mapOf
    }

    suspend fun hentAlleOppgaverUnderArbeid(): List<AlleOppgaverDto> {
        val kode6 = pepClient.harTilgangTilKode6()
        try {
            val json = using(sessionOf(dataSource)) {
                it.run(
                    queryOf(
                        """
                        select count(*) as antall,
                        (data -> 'fagsakYtelseType' ->> 'kode') as fagsakYtelseType,
                        (data -> 'behandlingType' ->> 'kode') as behandlingType,
                        not (data -> 'tilBeslutter') ::boolean as tilBehandling
                        from oppgave o where (data -> 'aktiv') ::boolean and (data -> 'kode6'):: Boolean =:kode6
                        group by  behandlingType, fagsakYtelseType, tilBehandling
                    """.trimIndent(),
                        mapOf("kode6" to kode6)
                    )
                        .map { row ->
                            AlleOppgaverDto(
                                FagsakYtelseType.fraKode(row.string("fagsakYtelseType")),
                                BehandlingType.fraKode(row.string("behandlingType")),
                                row.boolean("tilBehandling"),
                                row.int("antall")
                            )
                        }.asList
                )
            }
            Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
                .increment()

            return json
        } catch (e: Exception) {
            log.error("", e)
            return emptyList()
        }
    }

    suspend fun hentApneBehandlingerPerBehandlingtypeIdag(): List<AlleApneBehandlinger> {
        val kode6 = pepClient.harTilgangTilKode6()
        try {
            val json = using(sessionOf(dataSource)) {
                it.run(
                    queryOf(
                        """
                        select count(*) as antall,
                        (data -> 'behandlingType' ->> 'kode') as behandlingType
                        from oppgave o where (data -> 'aktiv') ::boolean and (data -> 'kode6'):: Boolean =:kode6
                        group by  behandlingType
                    """.trimIndent(),
                        mapOf("kode6" to kode6)
                    )
                        .map { row ->
                            AlleApneBehandlinger(
                                BehandlingType.fraKode(row.string("behandlingType")),
                                row.int("antall")
                            )
                        }.asList
                )
            }
            Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
                .increment()

            return json
        } catch (e: Exception) {
            log.error("", e)
            return emptyList()
        }
    }


    suspend fun hentOppgaverMedAktorId(aktørId: String): List<Oppgave> {
        val kode6 = pepClient.harTilgangTilKode6()
        val json: List<String> = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select data from oppgave where data ->> 'aktorId' = :aktorId",
                    mapOf("aktorId" to aktørId)
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        val oppgaver =
            json.map { s -> objectMapper().readValue(s, Oppgave::class.java) }.filter { it.kode6 == kode6 }.toList()
        oppgaver.forEach { refreshOppgave.trySend(it.eksternId) }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        return oppgaver
    }

    suspend fun hentOppgaverMedSaksnummer(saksnummer: String): List<Oppgave> {
        val kode6 = pepClient.harTilgangTilKode6()
        val json: List<String> = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select data from oppgave where lower(data ->> 'fagsakSaksnummer') = lower(:saksnummer) ",
                    mapOf("saksnummer" to saksnummer)
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        val oppgaver = json.map { objectMapper().readValue(it, Oppgave::class.java) }.filter { it.kode6 == kode6 }
        oppgaver.forEach { refreshOppgave.trySend(it.eksternId) }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return oppgaver
    }

    internal suspend fun hentAktiveOppgaverTotalt(): Int {
        val kode6 = pepClient.harTilgangTilKode6()
        var spørring = System.currentTimeMillis()
        val count: Int? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select count(*) as count from oppgave where (data -> 'aktiv') ::boolean and (data -> 'kode6'):: Boolean =:kode6 ",
                    mapOf("kode6" to kode6)
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        spørring = System.currentTimeMillis() - spørring
        log.info("Teller aktive oppgaver: $spørring ms")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
    }

    internal fun hentAktiveOppgaverTotaltIkkeSkjermede(): Int {
        val kode6 = false
        var spørring = System.currentTimeMillis()
        val count: Int? = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select count(*) as count from oppgave where (data -> 'aktiv') ::boolean and (data -> 'kode6'):: Boolean =:kode6 ",
                    mapOf("kode6" to kode6)
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        spørring = System.currentTimeMillis() - spørring
        log.info("Teller aktive oppgaver: $spørring ms") //hmmm, hva kan man forstå fra loggen når noen saksbehanlere får mye raskere spørring?
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
    }

    internal fun hentAktiveOppgaverTotaltPerBehandlingstypeOgYtelseType(
        fagsakYtelseType: FagsakYtelseType,
        behandlingType: BehandlingType
    ): Int {
        val count: Int? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select count(*) as count from oppgave where (data -> 'aktiv') ::boolean and (data -> 'behandlingType' ->> 'kode') =:behandlingType and (data -> 'fagsakYtelseType' ->> 'kode') =:fagsakYtelseType ",
                    mapOf("behandlingType" to behandlingType.kode, "fagsakYtelseType" to fagsakYtelseType.kode)
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        return count!!
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

    }

    internal fun hentMedBehandlingsstatus(behandlingStatus: BehandlingStatus): Int {
        var spørring = System.currentTimeMillis()
        val count: Int? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select count(*) as count from oppgave where not (data -> 'fagsakYtelseType' ->> 'kode' = 'FRISINN')  and (data -> 'behandlingStatus' ->> 'kode' = :status) ::boolean",
                    mapOf("status" to behandlingStatus.kode)
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        spørring = System.currentTimeMillis() - spørring
        log.info("Teller inaktive oppgaver: $spørring ms")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
    }

    internal fun hentInaktiveIkkeAvluttet(): Int {
        var spørring = System.currentTimeMillis()
        val count: Int? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select count(*) as count from oppgave where not (data -> 'fagsakYtelseType' ->> 'kode' = 'FRISINN')  and (data -> 'behandlingStatus' ->> 'kode' != 'AVSLU') and (data -> 'aktiv')::boolean = false",
                    mapOf()
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        spørring = System.currentTimeMillis() - spørring
        log.info("Teller inaktive oppgaver: $spørring ms")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
    }

    internal fun hentInaktiveIkkeAvluttetMedBehandlingStatus(behandlingStatus: BehandlingStatus): Int {
        var spørring = System.currentTimeMillis()
        val count: Int? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select count(*) as count from oppgave where not (data -> 'fagsakYtelseType' ->> 'kode' = 'FRISINN')  and (data -> 'behandlingStatus' ->> 'kode' = :status) and (data -> 'aktiv')::boolean = false",
                    mapOf("status" to behandlingStatus.kode)
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        spørring = System.currentTimeMillis() - spørring
        log.info("Teller inaktive oppgaver: $spørring ms")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
    }

    internal fun hentAutomatiskProsesserteTotalt(): Int {
        var spørring = System.currentTimeMillis()
        val count: Int? = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select count(*) as count from oppgave o left join reservasjon r using (id) where not (o.data -> 'fagsakYtelseType' ->> 'kode' = 'FRISINN')  and (o.data ->'behandlingStatus' ->> 'kode' = 'AVSLU') and r.id is null",
                    mapOf()
                )
                    .map { row ->
                        row.int("count")
                    }.asSingle
            )
        }
        spørring = System.currentTimeMillis() - spørring
        log.info("Teller automatiske oppgaver: $spørring ms")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
    }

    private val aktiveOppgaverCache = Cache<List<Oppgave>>()
    internal fun hentAktiveOppgaver(): List<Oppgave> {

        //TODO/FIXME legge på sist-endret i tabellen og _alltid_ oppfriske oppgaver som er endret
        //hvis vi gjør det kan mye av det som går mot databasen i spesielle spørringer istedet iterere over cachen
        val cacheObject = aktiveOppgaverCache.get("default")
        if (cacheObject != null) {
            return cacheObject.value
        }

        var spørring = System.currentTimeMillis()
        val json: List<String> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    "select data from oppgave where (data -> 'aktiv') ::boolean ",
                    mapOf()
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        spørring = System.currentTimeMillis() - spørring
        val serialisering = System.currentTimeMillis()
        val list = json.map { s -> objectMapper().readValue(s, Oppgave::class.java) }.toList()

        log.info("Henter aktive oppgaver: " + list.size + " oppgaver" + " serialisering: " + (System.currentTimeMillis() - serialisering) + " spørring: " + spørring)
        aktiveOppgaverCache.set("default", CacheObject(list))
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return list
    }

    internal fun hentAktiveOppgaversAksjonspunktliste(): List<AksjonspunktMock> {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        val json: List<List<AksjonspunktMock>> = using(sessionOf(dataSource)) { it ->
            it.run(
                queryOf(
                    "select (data -> 'aksjonspunkter' -> 'liste') punkt,  count(*) from oppgave where ((data -> 'aktiv') ::boolean and (data ->> 'system') = 'K9SAK') or ((data -> 'aktiv') ::boolean and (data ->> 'system') = 'PUNSJ')  group by data -> 'aksjonspunkter' -> 'liste'",
                    mapOf()
                )
                    .map { row ->
                        val map = objectMapper().readValue(
                            row.string("punkt"),
                            object : TypeReference<HashMap<String, String>>() {})
                        val antall = row.int("count")
                        val aksjonspunkter = map.keys.map { kode ->
                            val aksjonspunkt =
                                AksjonspunktDefWrapper.finnAlleAksjonspunkter().firstOrNull { a -> a.kode == kode }
                            aksjonspunkt
                        }
                            .map {
                                AksjonspunktMock(
                                    it?.kode ?: "Utdatert-dev",
                                    it?.navn ?: "Utdatert-dev",
                                    it?.aksjonspunktype ?: "Utdatert-dev",
                                    it?.behandlingsstegtype ?: "Utdatert-dev",
                                    "",
                                    "",
                                    it?.totrinn ?: false,
                                    antall = antall
                                )
                            }.toList()
                        aksjonspunkter
                    }.asList
            )
        }

        return json.flatten().groupBy { it.kode }.map { entry ->
            val aksjonspunkt = entry.value[0]
            aksjonspunkt.antall = entry.value.sumOf { it.antall }
            aksjonspunkt
        }
    }

    suspend fun hentOppgaveMedJournalpost(journalpostId: String) : List<Oppgave> {
        val kode6 = pepClient.harTilgangTilKode6()
        val json: List<String> = using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    "select data from oppgave where lower(data ->> 'journalpostId') = lower(:journalpostId) ",
                    mapOf("journalpostId" to journalpostId)
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        val oppgaver = json.map { objectMapper().readValue(it, Oppgave::class.java) }.filter { it.kode6 == kode6 }
        oppgaver.forEach { refreshOppgave.trySend(it.eksternId) }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return oppgaver
    }
}
