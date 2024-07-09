package no.nav.k9.los.domene.repository

import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.db.util.InClauseHjelper
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.OppgaveMedId
import no.nav.k9.los.domene.modell.AksjonspunktDefWrapper
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleApneBehandlinger
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverDto
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.mock.AksjonspunktMock
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource


class OppgaveRepository(
    private val dataSource: DataSource,
    private val pepClient: IPepClient,
    private val refreshOppgave: Channel<UUID>
) {
    private val log: Logger = LoggerFactory.getLogger(OppgaveRepository::class.java)

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
            LosObjectMapper.instance.readValue(json!!, Oppgave::class.java)
        } catch (e: NullPointerException) {
            log.error("feiler for denne json $json")
            throw e
        }
    }

    fun hentOppgaverSomMatcher(pleietrengendeAktørId: String, fagsakYtelseType: FagsakYtelseType): List<OppgaveMedId> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
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
                            LosObjectMapper.instance.readValue(row.string("data"), Oppgave::class.java)
                        )
                    }.asList
            )
        }
    }

    fun hentOppgaverSomMatcherSaksnummer(saksnummer: String): List<OppgaveMedId> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """
                    select id, data from oppgave where data ->> 'fagsakSaksnummer' = ?
                    """,
                    saksnummer
                )
                    .map { row ->
                        OppgaveMedId(
                            UUID.fromString(row.string("id")),
                            LosObjectMapper.instance.readValue(row.string("data"), Oppgave::class.java)
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
            LosObjectMapper.instance.readValue(json, Oppgave::class.java)
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
        val oppgaver = json.map { s -> LosObjectMapper.instance.readValue(s, Oppgave::class.java) }
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
                    f(LosObjectMapper.instance.readValue(run, Oppgave::class.java))
                } else {
                    f(null)
                }
                runBlocking {
                    oppgave.kode6 = sjekkKode6(oppgave)
                    oppgave.skjermet = sjekkKode7EllerEgenAnsatt(oppgave)
                }
                val json = LosObjectMapper.instance.writeValueAsString(oppgave)
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
            log.info("Spurte ikke etter noen oppgaveider")
            return emptyList()
        }

        val t0 = System.currentTimeMillis()

        val session = sessionOf(dataSource)
        val json: List<String> = using(session) {

            val spørring : String =
                "select data from oppgave " +
                        "where id in (${
                            IntRange(0, oppgaveiderList.size - 1).joinToString { t -> ":p$t" }
                        })"
            val parametre = IntRange(
                0,
                oppgaveiderList.size - 1
            ).associate { t -> "p$t" to oppgaveiderList[t].toString() as Any }

            //language=PostgreSQL
            it.run(
                queryOf(spørring, parametre)
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        val t1 = System.currentTimeMillis()
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()
        val resultat = json.filter { it.indexOf("oppgaver") == -1 } //TODO hvorfor?
            .map { s -> LosObjectMapper.instance.readValue(s, Oppgave::class.java) }
            .toList()
            .sortedBy { oppgave -> oppgave.behandlingOpprettet } //TODO burde gjøre sortering utenfor, siden det ulike domeneregler for sortering

        val t2 = System.currentTimeMillis()

        log.info("Hentet ${resultat.size} oppgaver. Etterspurte med ${oppgaveider.toSet().size} uuider. Hadde ${json.size} oppgaver før filtrering mot teksten 'oppgaver'. Brukte ${t1-t0} ms på spørring og ${t2-t1} ms på deserialisering")

        return resultat
    }

    fun hentOppgaverForPleietrengendeAktør(oppgaveIder: Collection<UUID>, aktørIder: Collection<String>): Set<UUID> {
        if (oppgaveIder.isEmpty() || aktørIder.isEmpty()) {
            return emptySet()
        }
        val unikeAktørIder = aktørIder.toSet()
        val unikeOppgaveIder = oppgaveIder.toSet()

        val session = sessionOf(dataSource)
        val alleOppgaverForAktørene: List<OppgaveIdAktørId> = using(session) {

            //language=PostgreSQL
            it.run(
                queryOf(
                    """
                         select id, data->'pleietrengendeAktørId' as aktor
                          from oppgave 
                          where data->>'pleietrengendeAktørId' in (${InClauseHjelper.tilParameternavn(unikeAktørIder, "a")})
                          """,
                    InClauseHjelper.parameternavnTilVerdierMap(aktørIder, "a")
                )
                    .map { row ->
                        OppgaveIdAktørId(UUID.fromString(row.string("id")), row.string("aktor"))
                    }.asList
            )
        }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        val resultat = alleOppgaverForAktørene
            .filter { unikeOppgaveIder.contains(it.oppgaveId) }
            .map { it.oppgaveId }
            .toSet()

        log.info("Spurte med ${oppgaveIder.size} oppgaver og ${aktørIder.size} aktører. Fant ${alleOppgaverForAktørene.size} oppgaver for aktørene, returnerer ${resultat.size} etter filtrering mot aktuelle oppgaver")

        return resultat
    }

    data class OppgaveIdAktørId (val oppgaveId : UUID, val aktørId : String) {

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
            json.map { s -> LosObjectMapper.instance.readValue(s, Oppgave::class.java) }.filter { it.kode6 == kode6 }
                .toList()
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
        val oppgaver =
            json.map { LosObjectMapper.instance.readValue(it, Oppgave::class.java) }.filter { it.kode6 == kode6 }
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
        log.info("Teller aktive oppgaver: $spørring ms")
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

        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return count!!
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

    internal fun hentAktiveUreserverteOppgaver(): List<Oppgave> {
        val t0 = System.currentTimeMillis()
        val json: List<String> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """with
                            aktive_reservasjoner as (select id from reservasjon where ((data -> 'reservasjoner' -> -1) ->> 'reservertTil')::timestamp > :now)
                         select data from oppgave o
                         where (data -> 'aktiv')::boolean
                         and not exists (select 1 from aktive_reservasjoner ar where ar.id = (o.data ->> 'eksternId')) 
                         """.trimMargin(),
                    mapOf(
                        "now" to LocalDateTime.now().truncatedTo(ChronoUnit.MICROS),
                    )
                )
                    .map { row ->
                        row.string("data")
                    }.asList
            )
        }
        val t1 = System.currentTimeMillis()
        val list =
            json.parallelStream().map { s -> LosObjectMapper.instance.readValue(s, Oppgave::class.java) }.toList()
        val t2 = System.currentTimeMillis()

        log.info("Hentet ${list.size} aktive ureserverte oppgaver. Serialisering: ${t2 - t1} ms, spørring: ${t1 - t0} ms")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return list
    }

    internal fun hentAktiveOppgaver(): List<UUID> {
        val t0 = System.currentTimeMillis()
        val resulat : List<UUID> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """  select (data ->> 'eksternId')::uuid as ekstern_id from oppgave
                         where (data -> 'aktiv')::boolean 
                         """.trimMargin(),
                    mapOf(
                    )
                )
                    .map { row ->
                        row.uuid("ekstern_id")
                    }.asList
            )
        }
        val t1 = System.currentTimeMillis()

        log.info("Hentet ${resulat.size} aktive behandlingUuid for aktive oppgaver. Operasjonen tok ${t1 - t0} ms.")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return resulat
    }

    internal fun hentAktiveK9sakOppgaver(): List<UUID> {
        val t0 = System.currentTimeMillis()
        val resulat : List<UUID> = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """  select (data ->> 'eksternId')::uuid as ekstern_id from oppgave
                         where (data -> 'aktiv')::boolean and (data ->> 'system') = 'K9SAK' 
                         """.trimMargin(),
                    mapOf(
                    )
                )
                    .map { row ->
                        row.uuid("ekstern_id")
                    }.asList
            )
        }
        val t1 = System.currentTimeMillis()

        log.info("Hentet ${resulat.size} aktive behandlingUuid for aktive k9sak-oppgaver oppgaver. Operasjonen tok ${t1 - t0} ms.")
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return resulat
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
                        val map = LosObjectMapper.instance.readValue(
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

    suspend fun hentOppgaveMedJournalpost(journalpostId: String): List<Oppgave> {
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
        val oppgaver =
            json.map { LosObjectMapper.instance.readValue(it, Oppgave::class.java) }.filter { it.kode6 == kode6 }
        oppgaver.forEach { refreshOppgave.trySend(it.eksternId) }
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        return oppgaver
    }
}
