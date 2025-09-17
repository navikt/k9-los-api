package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.DetaljerMetrikker
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgavefelt
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class AktivOppgaveRepository(val oppgavetypeRepository: OppgavetypeRepository) {

    companion object {

        private val log = LoggerFactory.getLogger(AktivOppgaveRepository::class.java)

        @WithSpan
        fun ajourholdAktivOppgave(oppgave: OppgaveV3, nyVersjon: Int, tx: TransactionalSession) {
            if (oppgave.status == Oppgavestatus.AAPEN || oppgave.status == Oppgavestatus.VENTER || oppgave.status == Oppgavestatus.UAVKLART) {
                val oppgaveId = DetaljerMetrikker.time("k9sakHistorikkvask", "oppdaterOppgaveV3Aktiv") {
                    oppdaterOppgaveV3Aktiv(
                        tx,
                        oppgave,
                        nyVersjon
                    )
                }
                DetaljerMetrikker.time("k9sakHistorikkvask", "oppdaterAktivOppgavefelter") {
                    oppdaterAktivOppgavefelter(
                        oppgaveId,
                        oppgave,
                        nyVersjon,
                        tx
                    )
                }
            } else {
                slettAktivOppgave(tx, oppgave, "Oppgave ${oppgave.id} har status ${oppgave.status} og fjernes derfor fra aktiv-tabellene")
            }
        }

        @WithSpan
        fun slettAktivOppgave(tx: TransactionalSession, oppgave: OppgaveV3, loggmeldingVedSlett : String? = null) {
            val id = hentOppgaveV3AktivId(tx, oppgave)
            if (id != null) {
                loggmeldingVedSlett?.let { log.info(loggmeldingVedSlett) }
                tx.run(
                    queryOf(
                        "delete from oppgavefelt_verdi_aktiv where oppgave_id = :id",
                        mapOf(
                            "id" to id.id
                        )
                    ).asUpdate
                )
                tx.run(
                    queryOf(
                        "delete from oppgave_v3_aktiv where id = :id",
                        mapOf(
                            "id" to id.id
                        )
                    ).asUpdate
                )
            }
        }

        private fun opprettOppgaveV3Aktiv(
            tx: TransactionalSession,
            oppgave: OppgaveV3,
            nyVersjon: Int
        ): AktivOppgaveId {
            return AktivOppgaveId(
                tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                        insert into oppgave_v3_aktiv (ekstern_id, ekstern_versjon, oppgavetype_id, status, versjon, kildeomrade, endret_tidspunkt, reservasjonsnokkel)
                        values(:eksternId, :eksternVersjon, :oppgavetypeId, cast(:status as oppgavestatus), :versjon, :kildeomrade, :endretTidspunkt, :reservasjonsnokkel)                   
                    """.trimIndent(),
                        mapOf(
                            "eksternId" to oppgave.eksternId,
                            "eksternVersjon" to oppgave.eksternVersjon,
                            "oppgavetypeId" to oppgave.oppgavetype.id,
                            "status" to oppgave.status.toString(),
                            "endretTidspunkt" to oppgave.endretTidspunkt,
                            "versjon" to nyVersjon,
                            "kildeomrade" to oppgave.kildeområde,
                            "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
                        )
                    )
                )!!
            )
        }

        private fun hentOppgaveV3AktivId(
            tx: TransactionalSession,
            oppgave: OppgaveV3
        ): AktivOppgaveId? {
            return tx.run(
                queryOf(
                    """ select id from oppgave_v3_aktiv where ekstern_id = :eksternId and kildeomrade = :kildeomrade """,
                    mapOf(
                        "eksternId" to oppgave.eksternId,
                        "kildeomrade" to oppgave.kildeområde
                    )
                )
                    .map { row -> AktivOppgaveId(row.long("id")) }.asSingle
            )
        }

        private fun oppdaterOppgaveV3Aktiv(
            tx: TransactionalSession,
            oppgave: OppgaveV3,
            nyVersjon: Int,
        ): AktivOppgaveId {
            val id = hentOppgaveV3AktivId(tx, oppgave) ?: return opprettOppgaveV3Aktiv(tx, oppgave, nyVersjon)

            tx.run(
                queryOf(
                    """
                update oppgave_v3_aktiv
                set
                        ekstern_versjon = :eksternVersjon,
                        status = cast(:status as oppgavestatus),
                        versjon = :versjon,
                        endret_tidspunkt = :endretTidspunkt,
                        reservasjonsnokkel = :reservasjonsnokkel 
                where ekstern_id = :eksternId and kildeomrade = :kildeomrade
                """,
                    mapOf(
                        "eksternId" to oppgave.eksternId,
                        "eksternVersjon" to oppgave.eksternVersjon,
                        "oppgavetypeId" to oppgave.oppgavetype.id,
                        "status" to oppgave.status.toString(),
                        "endretTidspunkt" to oppgave.endretTidspunkt,
                        "versjon" to nyVersjon,
                        "kildeomrade" to oppgave.kildeområde,
                        "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
                    )
                ).asUpdate
            )

            return id
        }

        private fun oppdaterAktivOppgavefelter(
            oppgaveId: AktivOppgaveId,
            oppgave: OppgaveV3,
            nyVersjon: Int,
            tx: TransactionalSession
        ) {
            check(nyVersjon >= 0) { "Ny versjon må være 0 eller høyere: $nyVersjon" }

            val eksisterendeFelter: List<OppgaveFeltverdi> = DetaljerMetrikker.time("k9sakHistorikkvask", "hentFeltverdier") {
                hentFeltverdier(
                    oppgaveId,
                    oppgave.oppgavetype,
                    tx
                )
            }

            val diffResultat = DetaljerMetrikker.time("k9sakHistorikkvask", "regnUtDiff") {
                regnUtDiff(
                    eksisterendeFelter,
                    oppgave.felter
                )
            }
            DetaljerMetrikker.time("k9sakHistorikkvask", "insertFelter") {
                insertFelter(
                    diffResultat.inserts,
                    oppgave,
                    oppgaveId,
                    tx
                )
            }
            DetaljerMetrikker.time("k9sakHistorikkvask", "oppdaterFelter") { oppdaterFelter(diffResultat.updates, tx) }
            DetaljerMetrikker.time("k9sakHistorikkvask", "slettFelter") { slettFelter(diffResultat.deletes, tx) }
            DetaljerMetrikker.observeTeller("k9sakHistorikkvask", "insertFelter", diffResultat.inserts.size)
            DetaljerMetrikker.observeTeller("k9sakHistorikkvask", "oppdaterFelter", diffResultat.updates.size)
            DetaljerMetrikker.observeTeller("k9sakHistorikkvask", "slettFelter", diffResultat.deletes.size)
        }

        private fun hentFeltverdier(
            oppgaveId: AktivOppgaveId,
            oppgavetype: Oppgavetype,
            tx: TransactionalSession
        ): List<OppgaveFeltverdi> {
            return tx.run(
                queryOf(
                    """
                    select * from oppgavefelt_verdi_aktiv where oppgave_id = :oppgaveId
                """.trimIndent(),
                    mapOf("oppgaveId" to oppgaveId.id)
                ).map { row ->
                    OppgaveFeltverdi(
                        id = row.long("id"),
                        oppgavefelt = oppgavetype.oppgavefelter.first { oppgavefelt ->
                            oppgavefelt.id == row.long("oppgavefelt_id")
                        },
                        verdi = row.string("verdi"),
                        verdiBigInt = row.longOrNull("verdi_bigint"),
                    )
                }.asList
            )
        }

        private fun insertFelter(
            inserts: Collection<Verdi>,
            oppgave: OppgaveV3,
            oppgaveId: AktivOppgaveId,
            tx: TransactionalSession
        ) {
            if (inserts.isEmpty()) {
                return
            }
            tx.batchPreparedNamedStatement(
                """
                insert into oppgavefelt_verdi_aktiv(oppgave_id, oppgavefelt_id, verdi, verdi_bigint, oppgavestatus, feltdefinisjon_ekstern_id, omrade_ekstern_id, oppgavetype_ekstern_id)
                        VALUES (:oppgaveId, :oppgavefeltId, :verdi, :verdi_bigint, cast(:oppgavestatus as oppgavestatus), :feltdefinisjon_ekstern_id, :omrade_ekstern_id, :oppgavetype_ekstern_id)
            """.trimIndent(),
                inserts.map { verdi ->
                    mapOf(
                        "oppgaveId" to oppgaveId.id,
                        "oppgavefeltId" to verdi.oppgavefeltId,
                        "verdi" to verdi.verdi,
                        "verdi_bigint" to verdi.verdiBigInt,
                        "oppgavestatus" to oppgave.status.kode,
                        "feltdefinisjon_ekstern_id" to verdi.oppgavefeltEksternId,
                        "omrade_ekstern_id" to oppgave.kildeområde,
                        "oppgavetype_ekstern_id" to oppgave.oppgavetype.eksternId,
                    )
                }
            )
        }

        private fun oppdaterFelter(updates: Map<Long, Verdi>, tx: TransactionalSession) {
            if (updates.isEmpty()) {
                return
            }
            tx.batchPreparedNamedStatement(
                "update oppgavefelt_verdi_aktiv set verdi = :verdi, verdi_bigint = :verdi_bigint where id = :id",
                updates.map { mapOf("id" to it.key, "verdi" to it.value.verdi, "verdi_bigint" to it.value.verdiBigInt) }.toList()
            )
        }

        private fun slettFelter(deletes: Collection<Long>, tx: TransactionalSession) {
            if (deletes.isEmpty()) {
                return
            }
            tx.batchPreparedNamedStatement(
                "delete from oppgavefelt_verdi_aktiv where id = :id",
                deletes.map { mapOf("id" to it) }.toList()
            )
        }

        private fun hentOppgavefelter(tx: TransactionalSession, oppgaveId: Long): List<Oppgavefelt> {
            return tx.run(
                queryOf(
                    """
                select ov.feltdefinisjon_ekstern_id as ekstern_id, ov.omrade_ekstern_id as omrade, fd.liste_type, f.pakrevd, ov.verdi, ov.verdi_bigint
                from oppgavefelt_verdi_aktiv ov 
                inner join oppgavefelt f on ov.oppgavefelt_id = f.id 
                inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id
                where ov.oppgave_id = :oppgaveId
                order by ov.feltdefinisjon_ekstern_id
                """.trimIndent(),
                    mapOf("oppgaveId" to oppgaveId)
                ).map { row ->
                    Oppgavefelt(
                        eksternId = row.string("ekstern_id"),
                        område = row.string("omrade"),
                        listetype = row.boolean("liste_type"),
                        påkrevd = row.boolean("pakrevd"),
                        verdi = row.string("verdi"),
                        verdiBigInt = row.longOrNull("verdi_bigint"),
                    )
                }.asList
            )
        }

        @VisibleForTesting
        fun regnUtDiff(eksisterende: List<OppgaveFeltverdi>, nye: List<OppgaveFeltverdi>): DiffResultat {
            val nyeVerdier = nye.map { Verdi(it.verdi, it.verdiBigInt, it.oppgavefelt.id!!, it.oppgavefelt.feltDefinisjon.eksternId) }.toSet()
            if (eksisterende.isEmpty()) {
                return DiffResultat(deletes = emptyList(), inserts = nyeVerdier, updates = emptyMap())
            }
            val eksisterendeVerdier = eksisterende.associate { Pair(Verdi(it.verdi, it.verdiBigInt, it.oppgavefelt.id!!, it.oppgavefelt.feltDefinisjon.eksternId), it.id!!) }
            val verdierBeggeSteder = eksisterendeVerdier.keys.intersect(nyeVerdier)

            val oppdaterMap: MutableMap<Long, Verdi> = HashMap()
            val kunEksisterende = (eksisterendeVerdier - verdierBeggeSteder).toMutableMap()
            val kunNye = (nyeVerdier - verdierBeggeSteder).toMutableSet()

            for (nyVerdi in ArrayList(kunNye)) {
                val match = kunEksisterende.entries.firstOrNull { it.key.oppgavefeltId == nyVerdi.oppgavefeltId }
                match?.let {
                    val id = it.value
                    oppdaterMap[id] = nyVerdi
                    kunEksisterende.remove(it.key)
                    kunNye.remove(nyVerdi)
                }
            }
            return DiffResultat(deletes = kunEksisterende.values, inserts = kunNye, updates = oppdaterMap)
        }

    }

    @WithSpan
    fun hentOppgaveForId(
        tx: TransactionalSession,
        aktivOppgaveId: AktivOppgaveId,
        now: LocalDateTime = LocalDateTime.now()
    ): Oppgave {
        val oppgave = tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3_aktiv ov
                where ov.id = :id
            """.trimIndent(),
                mapOf("id" to aktivOppgaveId.id)
            ).map { row ->
                mapOppgave(row, now, tx)
            }.asSingle
        ) ?: throw IllegalStateException("Fant ikke oppgave med id $aktivOppgaveId")

        return oppgave
    }

    @VisibleForTesting
    fun hentOppgaveForEksternId(
        tx: TransactionalSession,
        eksternOppgaveId: EksternOppgaveId,
        now: LocalDateTime = LocalDateTime.now()
    ): Oppgave? {
        return tx.run(
            queryOf(
                """
                select * 
                from oppgave_v3_aktiv ov
                where ov.ekstern_id = :ekstern_id
            """.trimIndent(),
                mapOf("ekstern_id" to eksternOppgaveId.eksternId)
            ).map { row ->
                mapOppgave(row, now, tx)
            }.asSingle
        )
    }

    @WithSpan
    fun hentK9sakParsakOppgaver(tx: TransactionalSession, oppgaver: Collection<Oppgave>): Set<EksternOppgaveId> {
        if (oppgaver.isEmpty()) {
            return emptySet()
        }
        return tx.run(
            queryOf(
                """                
                select oppg.ekstern_id as ekstern_id
                 from oppgave_v3_aktiv oppg
                 join oppgavetype ot on oppg.oppgavetype_id = ot.id
                 where 
                    ot.ekstern_id = 'k9sak'
                 and 
                    reservasjonsnokkel in (
                       select reservasjonsnokkel from oppgave_v3_aktiv where kildeomrade = 'K9' and ekstern_id in (${
                    InClauseHjelper.tilParameternavn(
                        oppgaver,
                        "o"
                    )
                })
                    )
            """.trimIndent(),
                InClauseHjelper.parameternavnTilVerdierMap(oppgaver.map { it.eksternId }, "o")
            ).map { row ->
                EksternOppgaveId("K9", row.string("ekstern_id"))
            }.asList
        ).toSet()
    }


    private fun mapOppgave(
        row: Row,
        now: LocalDateTime,
        tx: TransactionalSession
    ): Oppgave {
        val kildeområde = row.string("kildeomrade")
        val oppgaveTypeId = row.long("oppgavetype_id")
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(kildeområde, oppgaveTypeId, tx)
        val oppgavefelter = hentOppgavefelter(tx, row.long("id"))
        return Oppgave(
            eksternId = row.string("ekstern_id"),
            eksternVersjon = row.string("ekstern_versjon"),
            oppgavetype = oppgavetype,
            status = row.string("status"),
            endretTidspunkt = row.localDateTime("endret_tidspunkt"),
            kildeområde = row.string("kildeomrade"),
            felter = oppgavefelter,
            reservasjonsnøkkel = row.string("reservasjonsnokkel"),
            versjon = row.int("versjon")
        ).fyllDefaultverdier().utledTransienteFelter(now)
    }


    data class DiffResultat(
        val deletes: Collection<Long>,
        val inserts: Collection<Verdi>,
        val updates: Map<Long, Verdi>
    )

    data class Verdi(val verdi: String, val verdiBigInt: Long?, val oppgavefeltId: Long, val oppgavefeltEksternId: String)
}