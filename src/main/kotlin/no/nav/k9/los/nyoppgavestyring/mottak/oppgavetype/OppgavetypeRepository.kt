package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.feltutledere.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.utils.Cache
import org.slf4j.LoggerFactory

class OppgavetypeRepository(
    private val feltdefinisjonRepository: FeltdefinisjonRepository,
    private val områdeRepository: OmrådeRepository
    ) {

    private val log = LoggerFactory.getLogger(OppgavetypeRepository::class.java)
    private val oppgavetypeCache = Cache<Oppgavetyper>()

    fun hent(område: Område, definisjonskilde: String, tx: TransactionalSession): Oppgavetyper {
        val oppgavetyper = hent(område, tx)
        return Oppgavetyper(
            område = område,
            oppgavetyper = oppgavetyper.oppgavetyper.filter { oppgavetype ->  oppgavetype.definisjonskilde == definisjonskilde }.toSet()
        )
    }

    fun hentOppgavetype(område: String, oppgavetypeId: Long, tx: TransactionalSession): Oppgavetype {
        return hent(områdeRepository.hentOmråde(område, tx), tx).oppgavetyper.find { it.id!!.equals(oppgavetypeId) }
            ?: throw java.lang.IllegalStateException("Finner ikke omsøkt oppgavetype")
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
                                        feltdefinisjon.id!!.equals(row.long("feltdefinisjon_id"))
                                    }
                                        ?: throw IllegalStateException("Oppgavetypens oppgavefelt referer til udefinert feltdefinisjon eller feltdefinisjon utenfor området"),
                                    påkrevd = row.boolean("pakrevd"),
                                    defaultVerdi = row.string("defaultVerdi"),
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
            }
            tx.run(
                queryOf(
                    """
                    delete from oppgavetype
                    where id = :oppgavetypeId
                        and omrade_id = :omradeId""",
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
            val oppgavetypeId = tx.run(
                queryOf(
                    """
                    insert into oppgavetype(ekstern_id, omrade_id, definisjonskilde)
                    values(
                        :eksterntNavn,
                        :omradeId,
                        :definisjonskilde)""",
                    mapOf(
                        "eksterntNavn" to oppgavetype.eksternId,
                        "omradeId" to oppgavetype.område.id,
                        "definisjonskilde" to oppgavetype.definisjonskilde
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
                    "defaultverdi" to oppgavefelt.defaultVerdi,
                    "feltutleder" to oppgavefelt.feltutleder?.hentFeltutledernavn()
                ) //TODO: joine inn område_id? usikker på hva jeg synes om at feltdefinisjon.eksternt_navn alltid er prefikset med område
            ).asUpdate
        )
    }

    fun endre(endring: OppgavetypeEndring, tx: TransactionalSession) {
        val oppgaveFinnes = sjekkOmOppgaverFinnes(endring.oppgavetype.id!!, tx)
        endring.felterSomSkalLeggesTil.forEach { felt ->
            if (felt.påkrevd && felt.defaultVerdi.isNullOrEmpty()) {
                throw IllegalArgumentException("Kan ikke legge til påkrevd på eksisterende oppgave uten å oppgi defaultverdi")
            }
            leggTilOppgavefelt(tx, felt, endring.oppgavetype.id)
        }

        endring.felterSomSkalFjernes.forEach { felt ->
            //fjernFelt(felt, tx)
            throw NotImplementedError("Støtter foreløpig ikke å kunne fjerne felter fra oppgavetype")
        }

        endring.felterSomSkalEndresMedNyeVerdier.forEach { felter ->
            if (felter.innkommendeFelt.påkrevd && !felter.eksisterendeFelt.påkrevd && oppgaveFinnes) {
                throw IllegalArgumentException("Kan ikke endre felt til påkrevd når det finnes eksisterende oppgaver som mangler denne verdien")
            }
            oppdaterFelt(felter.eksisterendeFelt.id!!, felter.innkommendeFelt, tx)
        }
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
                    "defaultverdi" to innkommendeFelt.defaultVerdi,
                    "visPaOppgave" to innkommendeFelt.visPåOppgave, //TODO: Denne er ikke i basen ennå
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
                        inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id 
                    where fd.ekstern_id = :eksternId 
                """.trimIndent(),
                mapOf(
                    "eksternId" to felt.feltDefinisjon.eksternId
                )
            ).asUpdate
        )
    }

    fun invaliderCache() {
        oppgavetypeCache.clear()
    }
}