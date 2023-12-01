package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.feilhandtering.IllegalDeleteException
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.utils.Cache
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory

class FeltdefinisjonRepository(val områdeRepository: OmrådeRepository) {
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
                    visningsnavn = row.string("visningsnavn"),
                    listetype = row.boolean("liste_type"),
                    tolkesSom = row.string("tolkes_som"),
                    visTilBruker = row.boolean("vis_til_bruker"),
                    kokriterie = row.boolean("kokriterie"),
                    kodeverkreferanse = row.stringOrNull("kodeverkreferanse")?.let { Kodeverkreferanse(it) }
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
                    set visningsnavn = :visningsnavn,
                      liste_type = :listeType,
                      tolkes_som = :tolkesSom,
                      vis_til_bruker = :visTilBruker,
                      kokriterie = :kokriterie,
                      kodeverkreferanse = :kodeverkreferanse
                    WHERE omrade_id = :omradeId AND ekstern_id = :eksternId""",
                    mapOf(
                        "eksternId" to datatype.eksternId,
                        "omradeId" to område.id,
                        "visningsnavn" to datatype.visningsnavn,
                        "listeType" to datatype.listetype,
                        "tolkesSom" to datatype.tolkesSom,
                        "visTilBruker" to datatype.visTilBruker,
                        "kokriterie" to datatype.kokriterie,
                        "kodeverkreferanse" to datatype.kodeverkreferanse?.let { it.toDatabasestreng() }
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
                    insert into feltdefinisjon (
                      ekstern_id,
                      omrade_id,
                      visningsnavn,
                      liste_type,
                      tolkes_som,
                      vis_til_bruker,
                      kokriterie,
                      kodeverkreferanse
                    ) 
                    values (
                      :eksternId,
                      :omradeId,
                      :visningsnavn,
                      :listeType,
                      :tolkesSom,
                      :visTilBruker,
                      :kokriterie,
                      :kodeverkreferanse
                    )""",
                    mapOf(
                        "eksternId" to feltdefinisjon.eksternId,
                        "omradeId" to område.id,
                        "visningsnavn" to feltdefinisjon.visningsnavn,
                        "listeType" to feltdefinisjon.listetype,
                        "tolkesSom" to feltdefinisjon.tolkesSom,
                        "visTilBruker" to feltdefinisjon.visTilBruker,
                        "kokriterie" to feltdefinisjon.kokriterie,
                        "kodeverkreferanse" to feltdefinisjon.kodeverkreferanse?.toDatabasestreng()
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
                        and k.omrade_id = :omradeId
                    )
                """.trimIndent(),
                mapOf(
                    "eksternId" to kodeverk.eksternId,
                    "omradeId" to kodeverk.område.id!!
                )
            ).asUpdate
        )
    }

    fun lagre(kodeverk: Kodeverk, tx: TransactionalSession) {
        val kodeverkId = tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    insert into kodeverk (omrade_id, ekstern_id, beskrivelse, uttommende)
                    values (:omradeId, :eksternId, :beskrivelse, :uttommende)
                    on conflict (omrade_id, ekstern_id) do update set beskrivelse = :beskrivelse, uttommende = :uttommende 
                """.trimIndent(),
                mapOf(
                    "omradeId" to kodeverk.område.id,
                    "eksternId" to kodeverk.eksternId,
                    "beskrivelse" to kodeverk.beskrivelse,
                    "uttommende" to kodeverk.uttømmende,
                )
            )
        )
        tx.batchPreparedNamedStatement("""
            insert into kodeverk_verdi(kodeverk_id, verdi, visningsnavn, beskrivelse, favoritt)
            VALUES (:kodeverkId, :verdi, :visningsnavn, :beskrivelse, :favoritt)
            on conflict(kodeverk_id, verdi) do update set visningsnavn = :visningsnavn, beskrivelse = :beskrivelse, favoritt = :favoritt
        """.trimIndent(),
            kodeverk.verdier.map { verdi ->
                mapOf(
                    "kodeverkId" to kodeverkId,
                    "verdi" to verdi.verdi,
                    "visningsnavn" to verdi.visningsnavn,
                    "beskrivelse" to verdi.beskrivelse,
                    "favoritt" to verdi.favoritt
                )
            }
        )

        invaliderCache()
    }

    fun hentKodeverk(referanse: Kodeverkreferanse, tx: TransactionalSession) : Kodeverk {
        return hentKodeverk(områdeRepository.hentOmråde(referanse.område, tx), tx).hentKodeverk(referanse.eksternId)
    }

    fun hentKodeverk(områdeEksternId: String, tx: TransactionalSession): KodeverkForOmråde {
        return hentKodeverk(områdeRepository.hentOmråde(områdeEksternId, tx), tx)
    }

    fun hentKodeverk(område: Område, tx: TransactionalSession): KodeverkForOmråde {
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
                        uttømmende = kodeverkRow.boolean("uttommende"),
                        verdier = hentKodeverdier(tx, kodeverkRow)
                    )
                }.asList
            )

            KodeverkForOmråde(
                område = område,
                kodeverk = kodeverks
            )
        }
    }

    private fun hentKodeverdier(
        tx: TransactionalSession,
        kodeverkRow: Row
    ) = tx.run(
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
                beskrivelse = kodeverkverdiRow.stringOrNull("beskrivelse"),
                favoritt = kodeverkverdiRow.boolean("favoritt")
            )
        }.asList
    )

    fun invaliderCache() {
        kodeverkCache.clear()
    }
}