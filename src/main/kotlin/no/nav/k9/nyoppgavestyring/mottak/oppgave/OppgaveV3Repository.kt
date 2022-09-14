package no.nav.k9.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavetyper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class OppgaveV3Repository(
    private val områdeRepository: OmrådeRepository,
    //private val oppgavetypeRepository: OppgavetypeRepository
    ) {

    companion object {
        val oppgavetypeCache = HashMap<String, HashMap<String, Oppgavetype>>()
    }

    fun HashMap<Område, Oppgavetyper>.populer() {
        this.clear()
        //oppgavetypeRepository.hent()
    }

    private val log = LoggerFactory.getLogger(OppgaveV3Repository::class.java)

    //TODO: status enum

    fun lagre(oppgave: OppgaveV3, tx: TransactionalSession) {
        // hente ut nyeste versjon(ekstern_id, område) i basen, sette aktuell versjon til inaktiv
        val (eksisterendeId, eksisterendeVersjon) = hentVersjon(tx, oppgave) ?: Pair(null, null) //TODO: Herregud så stygt!

        eksisterendeId?.let { deaktiverVersjon(eksisterendeId, oppgave.endretTidspunkt, tx) }

        val nyVersjon = eksisterendeVersjon?.plus(1) ?: 0

        val oppgaveId = lagre(oppgave, nyVersjon, tx)
        lagreFeltverdier(oppgaveId, oppgave.felter, tx)
    }

    private fun lagre(oppgave: OppgaveV3, nyVersjon: Long, tx: TransactionalSession): Long  {
        return tx.updateAndReturnGeneratedKey(
            queryOf("""
                    insert into oppgave_v3(ekstern_id, ekstern_versjon, oppgavetype_id, status, versjon, aktiv, kildeomrade, endret_tidspunkt)
                    values(:eksternId, :eksternVersjon, :oppgavetypeId, :status, :versjon, :aktiv, :kildeomrade, :endretTidspunkt)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "eksternVersjon" to oppgave.eksternVersjon,
                    "oppgavetypeId" to oppgave.oppgavetype.id,
                    "status" to oppgave.status,
                    "endretTidspunkt" to oppgave.endretTidspunkt,
                    "versjon" to nyVersjon,
                    "aktiv" to true,
                    "kildeomrade" to oppgave.kildeområde
                )
            )
        )!!
    }

    private fun lagreFeltverdier(oppgaveId: Long, oppgaveFeltverdier: List<OppgaveFeltverdi>, tx: TransactionalSession) {
        oppgaveFeltverdier.forEach { feltverdi ->
            tx.run(
                queryOf(
                    """
                    insert into oppgavefelt_verdi(oppgave_id, oppgavefelt_id, verdi)
                    VALUES(:oppgaveId, :oppgavefeltId, :verdi)""".trimIndent(),
                    mapOf(
                        "oppgaveId" to oppgaveId,
                        "oppgavefeltId" to feltverdi.oppgavefelt.id,
                        "verdi" to  feltverdi.verdi
                    )
                ).asUpdate
            )
        }
    }

    private fun hentVersjon(tx: TransactionalSession, oppgave: OppgaveV3): Pair<Long, Long>? {
        //TODO: konsistenssjekk - skrive om til å hente alle oppgavene for gitt eksternId og sjekke at en og bare en versjon er aktiv = true
        return tx.run(
            queryOf(
                """
                select versjon, id
                from oppgave_v3 o
                where o.ekstern_id = :eksternId
                and o.kildeomrade = :omrade
                and versjon = 
                    (select max(versjon)
                     from oppgave_v3 oi
                     where oi.ekstern_id = o.ekstern_id
                     and oi.kildeomrade = o.kildeomrade)
                """.trimIndent(),
                mapOf(
                    "eksternId" to oppgave.eksternId,
                    "omrade" to oppgave.kildeområde
                )
            ).map { row -> Pair(row.long("id"), row.long("versjon")) }.asSingle
        )
    }

    fun hentOppgaveMedHistoriskeVersjoner(tx: TransactionalSession, eksternId: String): Set<OppgaveV3> {
        val oppgavetype = hentOppgavetype(tx, eksternId)
        return tx.run(
            queryOf(
                """
                    select * 
                    from oppgave_v3 ov
                    where ov.ekstern_id = :eksternId
                    order by versjon desc
                """.trimIndent(),
                mapOf("eksternId" to eksternId)
            ).map { row ->
                OppgaveV3(
                    id = row.long("id"),
                    eksternId = eksternId,
                    eksternVersjon = row.string("ekstern_versjon"),
                    oppgavetype = oppgavetype,
                    status = row.string("status"),
                    endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                    kildeområde = row.string("kildeomrade"),
                    felter = hentOppgavefeltverdier(tx, row.long("id"), oppgavetype)
                )
            }.asList
        ).toSet()
    }

    private fun hentOppgavetype(tx: TransactionalSession, oppgaveEksternId: String): Oppgavetype {
        return tx.run(
            queryOf("""
                    select * from oppgavetype where id = (select oppgavetype_id from oppgave_v3 where ekstern_id = :oppgaveEksternId
                """.trimIndent(),
                mapOf("oppgaveEksternId" to oppgaveEksternId)
            ).map { row ->
                Oppgavetype(
                    id = row.long("id"),
                    eksternId = row.string("ekstern_id"),
                    område = områdeRepository.hent(tx, row.long("omrade_id")),
                    definisjonskilde = row.string("definisjonskilde"),
                    oppgavefelter = hentOppgavefelter(tx, row.long("id"))
                )
            }.asSingle
        ) ?: throw IllegalStateException("Fant ingen oppgavetype for eksisterende oppgave: $oppgaveEksternId")
    }

    private fun hentOppgavefelter(tx: TransactionalSession, oppgavetypeId: Long): Set<Oppgavefelt> {
        return tx.run(
            queryOf(
                """
                            select *
                            from oppgavefelt o
                            where oppgavetype_id = :oppgavetypeId""",
                mapOf("oppgavetypeId" to oppgavetypeId)
            ).map { row ->
                Oppgavefelt(
                    id = row.long("id"),
                    feltDefinisjon = hentFeltdefinisjon(),
                    påkrevd = row.boolean("pakrevd"),
                    visPåOppgave = true
                )
            }.asList
        ).toSet()
    }

    private fun hentFeltdefinisjon(): no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon {
        TODO()
    }

    private fun hentOppgavefeltverdier(tx: TransactionalSession, oppgaveId: Long, oppgavetype: Oppgavetype): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                select *
                from oppgavefelt_verdi ov 
                where ov.oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId)
            ).map { row ->
                OppgaveFeltverdi(
                    id = row.long("id"),
                    oppgavefelt = oppgavetype.oppgavefelter.find { oppgavefelt ->
                        oppgavefelt.id == row.long("oppgavefelt_id")
                    } ?: throw IllegalStateException("Oppgavetype mangler oppgavens angitte oppgavefelt"),
                    verdi = row.string("verdi")
                )
            }.asList
        )
    }

    private fun deaktiverVersjon(eksisterendeId: Long, deaktivertTidspunkt: LocalDateTime, tx: TransactionalSession) {
        tx.run(
            queryOf("""
                update oppgave_v3 set aktiv = false, deaktivert_tidspunkt = :deaktivertTidspunkt where id = :id
            """.trimIndent(),
                mapOf(
                    "id" to eksisterendeId,
                    "deaktivertTidsunkt" to deaktivertTidspunkt
                )
            ).asUpdate
        )
    }

    fun idempotensMatch(tx: TransactionalSession, eksternId: String, eksternVersjon: String): Boolean {
        return tx.run(
            queryOf("""
                    select exists(
                        select *
                        from oppgave_v3 ov 
                        where ekstern_id = :eksternId
                        and ekstern_versjon = :eksternVersjon
                    )
                """.trimIndent(),
                mapOf(
                    "eksternId" to eksternId,
                    "eksternVersjon" to eksternVersjon
                )
            ).map { row -> row.boolean(1) }.asSingle
        )!!
    }

}