package no.nav.k9.los.driftsmelding

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.ManglerFlerområde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class DriftsmeldingRepository(
    private val dataSource: DataSource
) {
    fun lagreDriftsmelding(@ManglerFlerområde område: Områder, driftsmelding: DriftsmeldingDto) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    insert into driftsmeldinger as k (id, dato, melding, aktiv, omrade_id)
                    values (:id, :dato, :melding, :aktiv, (select id from omrade o where o.ekstern_id = :omrade_ekstern_id))
                 """,
                        mapOf(
                            "id" to driftsmelding.id,
                            "dato" to driftsmelding.dato,
                            "melding" to driftsmelding.melding,
                            "aktiv" to driftsmelding.aktiv,
                            "aktivert" to null,
                            "omrade_ekstern_id" to område.eksternId,
                        )
                    ).asUpdate
                )
            }
        }

    }

    fun setDriftsmelding(område: Områder, driftsmelding: DriftsmeldingSwitch, aktivert: LocalDateTime?) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                        queryOf(
                                """
                    update driftsmeldinger
                    set aktiv = :aktiv, aktivert = :aktivert
                    where id = :id AND omrade_id = (select id from omrade o where o.ekstern_id = :omrade_ekstern_id)
                 """,
                        mapOf(
                            "id" to driftsmelding.id,
                            "aktiv" to driftsmelding.aktiv,
                            "aktivert" to aktivert,
                            "omrade_ekstern_id" to område.eksternId,
                        )
                ).asUpdate
                )
            }
        }

    }

    fun hentAlle(område: Områder): List<DriftsmeldingDto> {
        return using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """select * from driftsmeldinger where omrade_id = (select id from omrade o where o.ekstern_id = :omrade_ekstern_id)""".trimIndent(),
                    mapOf("omrade_ekstern_id" to område.eksternId),
                )
                    .map { row ->
                        DriftsmeldingDto(
                            id = UUID.fromString(row.string("id")),
                            melding = row.string("melding"),
                            aktiv = row.boolean("aktiv"),
                            dato = row.localDateTime("dato"),
                            aktivert = row.localDateTimeOrNull("aktivert")
                        )
                    }.asList
            )
        }
    }

    fun slett(område: Områder, id: UUID) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                    delete from driftsmeldinger where id = :id AND omrade_id = (select id from omrade o where o.ekstern_id = :omrade_ekstern_id)           
                 """,
                        mapOf(
                            "id" to id.toString(),
                            "omrade_ekstern_id" to område.eksternId,
                        )
                    ).asUpdate
                )
            }
        }
    }

}
