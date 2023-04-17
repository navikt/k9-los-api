package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.feilhandtering.IllegalDeleteException
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.utils.Cache
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory

class FeltdefinisjonRepository {
    private val log = LoggerFactory.getLogger(FeltdefinisjonRepository::class.java)
    private val kodeverkCache = Cache<KodeverkForOmråde>()

    fun hent(område: Område, tx: TransactionalSession): Feltdefinisjoner {
        val feltdefinisjoner = tx.run(
            queryOf(
                """
                select * from feltdefinisjon 
                where omrade_id = :omradeId
                for update
            """.trimIndent(),
                mapOf("omradeId" to område.id)
            ).map { row ->
                Feltdefinisjon(
                    id = row.long("id"),
                    eksternId = row.string("ekstern_id"),
                    område = område,
                    listetype = row.boolean("liste_type"),
                    tolkesSom = row.string("tolkes_som"),
                    visTilBruker = row.boolean("vis_til_bruker"),
                    kodeverk = row.longOrNull("kodeverk_id")?.let { hentKodeverk(område, tx).hentKodeverk(kodeverkId = row.long("kodeverk_id")) }
                )
            }.asList
        )
        return Feltdefinisjoner(område, feltdefinisjoner.toSet())
    }

    fun fjern(sletteListe: Set<Feltdefinisjon>, tx: TransactionalSession) {
        sletteListe.forEach { datatype ->
            try {
                tx.run(
                    queryOf(
                        """
                        delete from feltdefinisjon where id = :id""",
                        mapOf(
                            "id" to datatype.id,
                        )
                    ).asUpdate
                )
            } catch (e: PSQLException) {
                if (e.sqlState.equals("23503")) {
                    throw IllegalDeleteException("Kan ikke slette feltdefinisjon som brukes av oppgavetype", e)
                } else {
                    throw e
                }
            }
        }
    }

    fun oppdater(oppdaterListe: Set<Feltdefinisjon>, område: Område, tx: TransactionalSession) {
        oppdaterListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                    update feltdefinisjon 
                    set liste_type = :listeType, tolkes_som = :tolkesSom, vis_til_bruker = :visTilBruker, kodeverk_id = :kodeverkId
                    WHERE omrade_id = :omradeId AND ekstern_id = :eksternId""",
                    mapOf(
                        "eksternId" to datatype.eksternId,
                        "omradeId" to område.id,
                        "listeType" to datatype.listetype,
                        "tolkesSom" to datatype.tolkesSom,
                        "visTilBruker" to datatype.visTilBruker,
                        "kodeverkId" to datatype.kodeverk?.id
                    )
                ).asUpdate
            )
        }
    }

    fun leggTil(leggTilListe: Set<Feltdefinisjon>, område: Område, tx: TransactionalSession) {
        leggTilListe.forEach { feltdefinisjon ->
            tx.run(
                queryOf(
                    """
                    insert into feltdefinisjon(ekstern_id, omrade_id, liste_type, tolkes_som, vis_til_bruker, kodeverk_id) 
                    values(:eksternId, :omradeId, :listeType, :tolkesSom, :visTilBruker, :kodeverkId)""",
                    mapOf(
                        "eksternId" to feltdefinisjon.eksternId,
                        "omradeId" to område.id,
                        "listeType" to feltdefinisjon.listetype,
                        "tolkesSom" to feltdefinisjon.tolkesSom,
                        "visTilBruker" to feltdefinisjon.visTilBruker,
                        "kodeverkId" to feltdefinisjon.kodeverk?.let { it.id!! }
                    )
                ).asUpdate
            )
        }
    }

    fun tømVerdierHvisKodeverkFinnes(kodeverk: Kodeverk, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                    delete
                    from kodeverk_verdi kv
                    where kv.id in (
                        select kvi.id from kodeverk_verdi kvi
                            inner join kodeverk k on kvi.kodeverk_id = k.id
                        where k.ekstern_id = :eksternId
                    )
                """.trimIndent(),
                mapOf(
                    "eksternId" to kodeverk.eksternId
                )
            ).asUpdate
        )
    }

    fun lagre(kodeverk: Kodeverk, tx: TransactionalSession) {
        val kodeverkId = tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    insert into kodeverk(omrade_id, ekstern_id, beskrivelse)
                    values(:omradeId, :eksternId, :beskrivelse)
                    on conflict (omrade_id, ekstern_id) do update set beskrivelse = :beskrivelse 
                """.trimIndent(),
                mapOf(
                    "omradeId" to kodeverk.område.id,
                    "eksternId" to kodeverk.eksternId,
                    "beskrivelse" to kodeverk.beskrivelse
                )
            )
        )
        tx.batchPreparedNamedStatement("""
            insert into kodeverk_verdi(kodeverk_id, verdi, visningsnavn, beskrivelse)
            VALUES (:kodeverkId, :verdi, :visningsnavn, :beskrivelse)
        """.trimIndent(),
            kodeverk.verdier.map { verdi ->
                mapOf(
                    "kodeverkId" to kodeverkId,
                    "verdi" to verdi.verdi,
                    "visningsnavn" to verdi.visningsnavn,
                    "beskrivelse" to verdi.beskrivelse
                )
            }
        )

        invaliderCache()
    }

    fun hentKodeverk(område: Område, tx: TransactionalSession) : KodeverkForOmråde {
        return kodeverkCache.hent(område.eksternId) {
            val kodeverks = tx.run(
                queryOf(
                    """
                        select * from kodeverk where omrade_id = :omradeId
                    """.trimIndent(),
                    mapOf(
                        "omradeId" to område.id
                    )
                ).map { kodeverkRow ->
                    Kodeverk(
                        id = kodeverkRow.long("id"),
                        område = område,
                        eksternId = kodeverkRow.string("ekstern_id"),
                        beskrivelse = kodeverkRow.stringOrNull("beskrivelse"),
                        verdier = tx.run(
                            queryOf(
                                """
                                select *
                                from kodeverk_verdi
                                where kodeverk_id = :kodeverkId""",
                                mapOf("kodeverkId" to kodeverkRow.long("id"))
                            ).map { kodeverkverdiRow ->
                                Kodeverkverdi(
                                    id = kodeverkverdiRow.long("id"),
                                    verdi = kodeverkverdiRow.string("verdi"),
                                    visningsnavn = kodeverkverdiRow.string("visningsnavn"),
                                    beskrivelse = kodeverkverdiRow.stringOrNull("beskrivelse")
                                )
                            }.asList
                        )
                    )
                }.asList
            )

            KodeverkForOmråde(
                område = område,
                kodeverk = kodeverks
            )
        }
    }

    fun invaliderCache() {
        kodeverkCache.clear()
    }
}