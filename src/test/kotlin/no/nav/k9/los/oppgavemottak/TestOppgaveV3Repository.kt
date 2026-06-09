package no.nav.k9.los.oppgavemottak

import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.infrastruktur.abac.PepClient
import no.nav.k9.los.oppgavemottak.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgaveuthenting.query.db.OppgaveV3Id
import javax.sql.DataSource

/**
 * Til hjelp under testing/debugging
 */
class TestOppgaveV3Repository(
    private val dataSource: DataSource,
    private val pepClient: PepClient
) {
    fun hentAlleOppgaver(): List<OppgaveV3> {
        val områdeRepository = OmrådeRepository(dataSource)
        val feltdefinisjonRepository = FeltdefinisjonRepository(
            områdeRepository
        )
        val transactionalManager = TransactionalManager(dataSource)
        val oppgavetypeRepository = OppgavetypeRepository(
            dataSource,
            feltdefinisjonRepository,
            områdeRepository,
            GyldigeFeltutledere(SaksbehandlerRepository(dataSource, pepClient, transactionalManager))
        )
        return TransactionalManager(dataSource).transaction { tx ->
            tx.run(
                queryOf(
                    """
                    select ot.ekstern_id as ot_ekstern_id, * from oppgave_v3 inner join oppgavetype ot on ot.id = oppgavetype_id where aktiv = true
                """.trimIndent()
                ).map { row ->
                    val oppgavetype =
                        oppgavetypeRepository.hentOppgavetype(row.string("kildeomrade"), row.string("ot_ekstern_id"))
                    OppgaveV3(
                        id = OppgaveV3Id(row.long("id")),
                        eksternId = row.string("ekstern_id"),
                        eksternVersjon = row.string("ekstern_versjon"),
                        oppgavetype = oppgavetype,
                        status = Oppgavestatus.valueOf(row.string("status")),
                        endretTidspunkt = row.localDateTime("endret_tidspunkt"),
                        kildeområde = row.string("kildeomrade"),
                        reservasjonsnøkkel = row.stringOrNull("reservasjonsnokkel") ?: "mangler_historikkvask",
                        aktiv = row.boolean("aktiv"),
                        felter = hentFeltverdier(OppgaveV3Id(row.long("id")), oppgavetype, tx)
                    )
                }.asList
            )
        }
    }

    private fun hentFeltverdier(
        oppgaveId: OppgaveV3Id,
        oppgavetype: Oppgavetype,
        tx: TransactionalSession
    ): List<OppgaveFeltverdi> {
        return tx.run(
            queryOf(
                """
                    select * from oppgavefelt_verdi where oppgave_id = :oppgaveId
                """.trimIndent(),
                mapOf("oppgaveId" to oppgaveId.id)
            ).map { row ->
                OppgaveFeltverdi(
                    id = row.long("id"),
                    oppgavefelt = oppgavetype.oppgavefelter.first { oppgavefelt ->
                        oppgavefelt.id == row.long("oppgavefelt_id")
                    },
                    verdi = row.string("verdi"),
                    verdiBigInt = null
                )
            }.asList
        )
    }


}