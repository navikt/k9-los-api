package no.nav.k9.los.domene.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingDto
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingSwitch
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class DriftsmeldingRepository(
    private val dataSource: DataSource
) {
    fun lagreDriftsmelding(driftsmelding: DriftsmeldingDto) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->

                //language=PostgreSQL
                tx.run(
                    queryOf(
                        """
                    insert into driftsmeldinger as k (id, dato, melding, aktiv)
                    values (:id, :dato, :melding, :aktiv)                 
                       
                 """,
                        mapOf(
                            "id" to driftsmelding.id,
                            "dato" to driftsmelding.dato,
                            "melding" to driftsmelding.melding,
                            "aktiv" to driftsmelding.aktiv,
                            "aktivert" to null
                        )
                    ).asUpdate
                )
            }
        }

    }

    fun setDriftsmelding(driftsmelding: DriftsmeldingSwitch, aktivert: LocalDateTime?) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->

                //language=PostgreSQL
                tx.run(
                        queryOf(
                                """
                    update driftsmeldinger
                    set aktiv = :aktiv, aktivert = :aktivert
                    where id = :id                
                       
                 """,
                        mapOf(
                                "id" to driftsmelding.id,
                                "aktiv" to driftsmelding.aktiv,
                                "aktivert" to aktivert
                        )
                ).asUpdate
                )
            }
        }

    }

    fun hentAlle(): List<DriftsmeldingDto> {
        return using(sessionOf(dataSource)) {
            //language=PostgreSQL
            it.run(
                queryOf(
                    """select * from driftsmeldinger""".trimIndent()
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

    fun slett(id: UUID) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->

                //language=PostgreSQL
                tx.run(
                    queryOf(
                        """
                    delete from driftsmeldinger where id = :id            
                 """,
                        mapOf(
                            "id" to id.toString()
                        )
                    ).asUpdate
                )
            }
        }
    }

}
