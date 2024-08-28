package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.feilhandtering.IllegalDeleteException
import no.nav.k9.los.nyoppgavestyring.feilhandtering.MissingDefaultException
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.utils.Cache
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OppgavetypeRepository(
    private val dataSource: DataSource,
    private val feltdefinisjonRepository: FeltdefinisjonRepository,
    private val områdeRepository: OmrådeRepository
    ) {

    private val log = LoggerFactory.getLogger(OppgavetypeRepository::class.java)
    private val oppgavetypeCache = Cache<String, Oppgavetyper>(cacheSizeLimit = null)

    fun hent(område: Område, definisjonskilde: String, tx: TransactionalSession): Oppgavetyper {
        val oppgavetyper = hent(område, tx)
        return Oppgavetyper(
            område = område,
            oppgavetyper = oppgavetyper.oppgavetyper.filter { oppgavetype ->  oppgavetype.definisjonskilde == definisjonskilde }.toSet()
        )
    }

    fun hentOppgavetype(område: String, eksternId: String): Oppgavetype {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                hentOppgavetype(område, eksternId, tx)
            }
        }
    }

    fun hentOppgavetype(område: String, eksternId: String, tx: TransactionalSession): Oppgavetype {
        return hentOppgavetype(områdeRepository.hentOmråde(område, tx), eksternId, tx)
    }

    fun hentOppgavetype(område: Område, eksternId: String, tx: TransactionalSession): Oppgavetype {
        return hent(område, tx).oppgavetyper.find { it.eksternId == eksternId }
            ?: throw IllegalArgumentException("Finner ikke oppgavetype: ${eksternId} for område: ${område.eksternId}")
    }

    fun hentOppgavetype(område: String, oppgavetypeId: Long, tx: TransactionalSession): Oppgavetype {
        return hent(områdeRepository.hentOmråde(område, tx), tx).oppgavetyper.find { it.id!! == oppgavetypeId }
            ?: throw IllegalArgumentException("Finner ikke omsøkt oppgavetypeId: ${oppgavetypeId} for område: ${område}")
    }

    fun hent(område: Område, tx: TransactionalSession): Oppgavetyper {
        return oppgavetypeCache.hent(område.eksternId) {
            val feltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
            val oppgavetypeListe = tx.run(
                queryOf(
                    """
                select * from oppgavetype where omrade_id = :omradeId
            """.trimIndent(),
                    mapOf(
                        "omradeId" to område.id
                    )
                ).map { oppgavetypeRow ->
                    Oppgavetype(
                        id = oppgavetypeRow.long("id"),
                        eksternId = oppgavetypeRow.string("ekstern_id"),
                        område = område,
                        oppgavebehandlingsUrlTemplate = oppgavetypeRow.stringOrNull("oppgavebehandlingsUrlTemplate"),
                        oppgavefelter = tx.run(
                            queryOf(
                                """
                            select *
                            from oppgavefelt o
                            where oppgavetype_id = :oppgavetypeId""",
                                mapOf("oppgavetypeId" to oppgavetypeRow.long("id"))
                            ).map { row ->
                                Oppgavefelt(
                                    id = row.long("id"),
                                    feltDefinisjon = feltdefinisjoner.feltdefinisjoner.find { feltdefinisjon ->
                                        feltdefinisjon.id!! == row.long("feltdefinisjon_id")
                                    }
                                        ?: throw IllegalStateException("Oppgavetypens oppgavefelt referer til udefinert feltdefinisjon eller feltdefinisjon utenfor området"),
                                    påkrevd = row.boolean("pakrevd"),
                                    defaultverdi = row.stringOrNull("defaultVerdi"),
                                    visPåOppgave = true,
                                    feltutleder = row.stringOrNull("feltutleder")?.let {
                                            GyldigeFeltutledere.hentFeltutleder(it)
                                        }
                                )
                            }.asList
                        ).toSet(),
                        definisjonskilde = oppgavetypeRow.string("definisjonskilde")
                    )
                }.asList
            )
            Oppgavetyper(område, oppgavetypeListe.toSet())
        }
    }

    fun fjern(oppgavetyper: Oppgavetyper, tx: TransactionalSession) {
        oppgavetyper.oppgavetyper.forEach { oppgavetype ->
            oppgavetype.oppgavefelter.forEach { oppgavefelt ->
                try {
                    tx.run(
                        queryOf(
                            """
                         delete from oppgavefelt
                         where oppgavetype_id = :oppgavetypeId 
                            and feltdefinisjon_id = :feltdefinisjonId""",
                            mapOf(
                                "oppgavetypeId" to oppgavetype.id,
                                "feltdefinisjonId" to oppgavefelt.feltDefinisjon.id
                            )
                        ).asUpdate
                    )
                }  catch (e: PSQLException) {
                    if (e.sqlState.equals("23503")) {
                        throw IllegalDeleteException("Kan ikke slette oppgavefelt som brukes av oppgave", e)
                    } else {
                        throw e
                    }
                }
            }
            tx.run(
                queryOf(
                    """
                    delete from oppgavetype
                    where id = :oppgavetypeId
                        and omrade_id =2d""",
                    mapOf(
                        "oppgavetypeId" to oppgavetype.id,
                        "omradeId" to oppgavetyper.område.id
                    )
                ).asUpdate
            )
        }
    }

    fun leggTil(oppgavetyper: Oppgavetyper, tx: TransactionalSession) {
        oppgavetyper.oppgavetyper.forEach { oppgavetype ->
            log.info("legger til oppgavetype: ${oppgavetype.eksternId} med behandlingsUrlTemplate: ${oppgavetype.oppgavebehandlingsUrlTemplate}")
            val oppgavetypeId = tx.run(
                queryOf(
                    """
                    insert into oppgavetype(ekstern_id, omrade_id, definisjonskilde, oppgavebehandlingsUrlTemplate)
                    values(
                        :eksterntNavn,
                        :omradeId,
                        :definisjonskilde,
                        :oppgavebehandlingsUrlTemplate)""",
                    mapOf(
                        "eksterntNavn" to oppgavetype.eksternId,
                        "omradeId" to oppgavetype.område.id,
                        "definisjonskilde" to oppgavetype.definisjonskilde,
                        "oppgavebehandlingsUrlTemplate" to oppgavetype.oppgavebehandlingsUrlTemplate,
                    )
                ).asUpdateAndReturnGeneratedKey
            )!!
            oppgavetype.oppgavefelter.forEach { oppgavefelt ->
                leggTilOppgavefelt(tx, oppgavefelt, oppgavetypeId)
            }
        }
    }

    private fun leggTilOppgavefelt(
        tx: TransactionalSession,
        oppgavefelt: Oppgavefelt,
        oppgavetypeId: Long
    ) {
        tx.run(
            queryOf(
                """
                insert into oppgavefelt(feltdefinisjon_id, oppgavetype_id, pakrevd, defaultverdi, feltutleder)
                values(
                    :feltdefinisjonId,
                    :oppgavetypeId,
                    :paakrevd,
                    :defaultverdi,
                    :feltutleder)""",
                mapOf(
                    "feltdefinisjonId" to oppgavefelt.feltDefinisjon.id,
                    "oppgavetypeId" to oppgavetypeId,
                    "paakrevd" to oppgavefelt.påkrevd,
                    "defaultverdi" to oppgavefelt.defaultverdi,
                    "feltutleder" to oppgavefelt.feltutleder?.hentFeltutledernavn()
                ) //TODO: joine inn område_id? usikker på hva jeg synes om at feltdefinisjon.eksternt_navn alltid er prefikset med område
            ).asUpdate
        )
    }

    fun endre(endring: OppgavetypeEndring, tx: TransactionalSession) {
        val oppgavetypeId = hentOppgavetype(endring.oppgavetype.område, endring.oppgavetype.eksternId, tx).id!!
        val oppgaveFinnes = sjekkOmOppgaverFinnes(oppgavetypeId, tx)
        endring.felterSomSkalLeggesTil.forEach { felt ->
            if (oppgaveFinnes && felt.påkrevd && felt.defaultverdi.isNullOrEmpty()) {
                throw MissingDefaultException("Kan ikke legge til påkrevd på eksisterende oppgave uten å oppgi defaultverdi")
            }
            leggTilOppgavefelt(tx, felt, oppgavetypeId)
        }

        endring.felterSomSkalFjernes.forEach { felt ->
            fjernFelt(felt, tx)
        }

        endring.felterSomSkalEndresMedNyeVerdier.forEach { felter ->
            if (oppgaveFinnes && felter.innkommendeFelt.påkrevd && !felter.eksisterendeFelt.påkrevd && felter.innkommendeFelt.defaultverdi == null) {
                throw MissingDefaultException("Kan ikke legge til påkrevd på eksisterende oppgave uten å oppgi defaultverdi")
            }
            oppdaterFelt(felter.eksisterendeFelt.id!!, felter.innkommendeFelt, tx)
        }
        oppdaterOppgavebehandlingsUrlTemplate(endring, tx)
    }

    private fun oppdaterOppgavebehandlingsUrlTemplate(endring: OppgavetypeEndring, tx: TransactionalSession) {
        log.info("lagrer urltemplate: ${endring.oppgavetype.oppgavebehandlingsUrlTemplate} på oppgavetype ${endring.oppgavetype.eksternId}")
        tx.run(
            queryOf(
                """
                    update oppgavetype
                    set oppgavebehandlingsUrlTemplate = :oppgavebehandlingsUrlTemplate
                    where ekstern_id = :ekstern_id 
                """.trimIndent(),
                mapOf(
                    "oppgavebehandlingsUrlTemplate" to endring.oppgavetype.oppgavebehandlingsUrlTemplate,
                    "ekstern_id" to endring.oppgavetype.eksternId
                )
            ).asUpdate
        )
    }

    private fun oppdaterFelt(id: Long, innkommendeFelt: Oppgavefelt, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                update oppgavefelt
                set pakrevd = :pakrevd, defaultverdi = :defaultverdi, feltutleder = :feltutleder
                where id = :id
            """.trimIndent(),
                mapOf(
                    "pakrevd" to innkommendeFelt.påkrevd,
                    "defaultverdi" to innkommendeFelt.defaultverdi,
                    "visPaOppgave" to innkommendeFelt.visPåOppgave,
                    "id" to id,
                    "feltutleder" to innkommendeFelt.feltutleder?.hentFeltutledernavn()
                )
            ).asUpdate
        )
    }

    private fun sjekkOmOppgaverFinnes(oppgavetypeId: Long, tx: TransactionalSession): Boolean {
        val verdi = tx.run(
            queryOf(
                """
                    select id
                    from oppgave_v3 ov 
                    where oppgavetype_id = :oppgavetypeId
                    limit 1
                """.trimIndent(),
                mapOf(
                    "oppgavetypeId" to oppgavetypeId
                )
            ).map { row ->
                row.long("id")
            }.asSingle
        )
        return verdi != null
    }

    private fun fjernFelt(felt: Oppgavefelt, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                    delete
                    from oppgavefelt f
                    where f.id = :oppgavefelt_id
                """.trimIndent(),
                mapOf(
                    "oppgavefelt_id" to felt.id!!
                )
            ).asUpdate
        )
    }

    fun invaliderCache() {
        oppgavetypeCache.clear()
    }
}