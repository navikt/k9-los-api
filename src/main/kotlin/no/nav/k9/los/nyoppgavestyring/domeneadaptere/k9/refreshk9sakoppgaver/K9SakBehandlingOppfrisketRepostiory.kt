package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class K9SakBehandlingOppfrisketRepostiory(private val dataSource: DataSource) {
    fun hentAlleOppfrisketEtter(tidspunkt: LocalDateTime): List<K9sakBehandlingOppfrisketTidspunkt> {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(queryOf(
                    "select behandling_uuid, tidspunkt from k9sak_behandling_oppfrisket where tidspunkt > :tidspunkt",
                    mapOf("tidspunkt" to tidspunkt)
                )
                    .map { row ->
                        K9sakBehandlingOppfrisketTidspunkt(
                            row.uuid("behandling_uuid"),
                            row.localDateTime("tidspunkt")
                        )
                    }
                    .asList
                )
            }
        }
    }

    fun registrerOppfrisket(oppfrisket: List<K9sakBehandlingOppfrisketTidspunkt>) {
        return using(sessionOf(dataSource)) {
            session -> session.transaction { tx ->
                oppfrisket.sortedBy { it.behandlingUuid }.forEach {registrerOppfrisket(tx, it)}
            }
        }
    }

    fun registrerOppfrisket(tx: TransactionalSession, oppfrisket: K9sakBehandlingOppfrisketTidspunkt) {
        tx.run(
            queryOf(
                """
                        insert into k9sak_behandling_oppfrisket (behandling_uuid, tidspunkt) 
                         values (:behandlingUuid, :tidspunkt) 
                         on conflict (behandling_uuid) 
                         do update set tidspunkt = :tidspunkt """,
                mapOf(
                    "behandlingUuid" to oppfrisket.behandlingUuid,
                    "tidspunkt" to oppfrisket.tidspunkt
                )
            )
                .asUpdate
        )
    }

    fun slettOppfrisketFør(tidspunkt: LocalDateTime) {
        return using(sessionOf(dataSource)) {
            it.transaction { tx ->
                tx.run(
                    queryOf(
                        "delete from k9sak_behandling_oppfrisket where tidspunkt < :tidspunkt",
                        mapOf("tidspunkt" to tidspunkt)
                    )
                        .asUpdate
                )
            }
        }
    }

}

data class K9sakBehandlingOppfrisketTidspunkt(val behandlingUuid: UUID, val tidspunkt: LocalDateTime)