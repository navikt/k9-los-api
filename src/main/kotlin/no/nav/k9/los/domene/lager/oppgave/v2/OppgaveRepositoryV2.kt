package no.nav.k9.los.domene.lager.oppgave.v2

import kotliquery.*
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.UpdateQueryAction
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class OppgaveRepositoryV2(
    private val dataSource: DataSource
) {

    private val log = LoggerFactory.getLogger(OppgaveRepositoryV2::class.java)

    fun hentBehandling(eksternReferanse: String): Behandling? {
        return using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                hentBehandling(eksternReferanse, tx)
            }
        }
    }

    fun hentBehandling(eksternReferanse: String, tx: TransactionalSession): Behandling? {
        val oppgaver = tx.run(hentOppgaver(eksternReferanse))
        return tx.run(hentBehandling(eksternReferanse, oppgaver))
    }

    private fun hentBehandling(
        eksternReferanse: String,
        oppgaver: Collection<OppgaveV2>
    ): NullableResultQueryAction<Behandling> {
        return queryOf(
            """
                        select
                            id,
                            fagsystem,
                            ytelse_type,
                            behandling_type,
                            soekers_id,
                            opprettet,
                            sist_endret,
                            ferdigstilt_tidspunkt
                        from behandling 
                        WHERE ekstern_referanse = :ekstern_referanse
                        FOR UPDATE
                    """,
            mapOf("ekstern_referanse" to eksternReferanse)
        ).map { row -> row.tilBehandling(eksternReferanse, oppgaver) }.asSingle
    }

    private fun Row.tilBehandling(
        eksternReferanse: String,
        oppgaver: Collection<OppgaveV2>
    ): Behandling {
        return Behandling(
            id = long("id"),
            eksternReferanse = eksternReferanse,
            oppgaver = oppgaver.toMutableSet(),
            fagsystem = Fagsystem.fraKode(string("fagsystem")),
            ytelseType = FagsakYtelseType.fraKode(string("ytelse_type")),
            behandlingType = stringOrNull("behandling_type"),
            opprettet = localDateTime("opprettet"),
            sistEndret = localDateTimeOrNull("sist_endret"),
            søkersId = stringOrNull("soekers_id")?.let { Ident(it, Ident.IdType.AKTØRID) },
            data = null
        ).also {
            it.ferdigstilt = localDateTimeOrNull("ferdigstilt_tidspunkt")
        }
    }

    private fun hentOppgaver(eksternReferanse: String): ListResultQueryAction<OppgaveV2> {
        return queryOf(
            """
                        select
                            id,
                            ekstern_referanse, 
                            oppgave_status, 
                            oppgave_kode, 
                            beslutter,
                            opprettet,
                            sist_endret,
                            ferdigstilt_tidspunkt,
                            ferdigstilt_saksbehandler,
                            ferdigstilt_enhet,
                            frist
                        from oppgave_v2 
                        WHERE ekstern_referanse = :ekstern_referanse
                    """,
            mapOf(
                    "ekstern_referanse" to eksternReferanse,
                )
            ).map { row ->
                OppgaveV2(
                    id = row.long("id"),
                    eksternReferanse = eksternReferanse,
                    opprettet = row.localDateTime("opprettet"),
                    sistEndret = row.localDateTime("sist_endret"),
                    oppgaveStatus = OppgaveStatus.valueOf(row.string("oppgave_status")),
                    oppgaveKode = row.string("oppgave_kode"),
                    erBeslutter = row.boolean("beslutter"),
                    frist = row.localDateTimeOrNull("frist")
                ).also {
                    row.localDateTimeOrNull("ferdigstilt_tidspunkt")?.let { ferdigstiltTidspunkt ->
                        it.ferdigstilt = OppgaveV2.Ferdigstilt(
                            tidspunkt = ferdigstiltTidspunkt,
                            ansvarligSaksbehandlerIdent = row.stringOrNull("ferdigstilt_saksbehandler"),
                            behandlendeEnhet = row.stringOrNull("ferdigstilt_enhet")
                        )
                    }
                }
            }.asList
    }

    fun lagre(behandling: Behandling) {
        using(sessionOf(dataSource)) {
            it.transaction { tx ->
                lagre(behandling, tx)
            }
        }
    }

    fun lagre(behandling: Behandling, tx: TransactionalSession) {
        val dbId = behandling.id?.also { tx.run(update(behandling).asUpdate) }
            ?: tx.updateAndReturnGeneratedKey(insert(behandling))?.also { behandling.id = it }

        behandling.oppgaver().forEach { oppgave ->
            oppgave.id?.also { tx.run(update(oppgave)) }
                ?: tx.updateAndReturnGeneratedKey(insert(dbId!!, oppgave))?.also { oppgave.id = it }
        }
    }

    private fun insert(behandling: Behandling): Query {
        log.info("Lagrer ny behandling ${behandling.eksternReferanse}")
        return queryOf(
            """
                insert into behandling (
                    ekstern_referanse,
                    fagsystem,
                    ytelse_type,
                    behandling_type,
                    opprettet,
                    sist_endret,
                    soekers_id,
                    ferdigstilt_tidspunkt
                ) VALUES (
                    :ekstern_referanse,
                    :fagsystem,
                    :ytelse_type,
                    :behandling_type,
                    :opprettet,
                    :sist_endret,
                    :soekers_id,
                    :ferdigstilt_tidspunkt
                )
                """, mapOf(
                "ekstern_referanse" to behandling.eksternReferanse,
                "fagsystem" to behandling.fagsystem.kode,
                "ytelse_type" to behandling.ytelseType.kode,
                "behandling_type" to behandling.behandlingType,
                "opprettet" to behandling.opprettet,
                "sist_endret" to behandling.sistEndret,
                "soekers_id" to behandling.søkersId?.id,
                "ferdigstilt_tidspunkt" to behandling.ferdigstilt,
            )
        )
    }


    private fun update(behandling: Behandling): Query {
        log.info("Oppdaterer eksisterende behandling ${behandling.eksternReferanse}")
        return queryOf(
    """
                UPDATE behandling SET
                    sist_endret = :sist_endret,
                    soekers_id = :soekers_id,
                    ferdigstilt_tidspunkt = :ferdigstilt_tidspunkt
                WHERE id = :id
            """, mapOf(
                "id" to behandling.id,
                "sist_endret" to behandling.sistEndret,
                "soekers_id" to behandling.søkersId?.id,
                "ferdigstilt_tidspunkt" to behandling.ferdigstilt
            )
        )
    }

    private fun insert(behandlingId: Long, oppgave: OppgaveV2): Query {
        log.info("Lagrer ny oppgave for ${oppgave.oppgaveKode} på referanse: ${oppgave.eksternReferanse}")
        return queryOf(
            """
                insert into oppgave_v2 (
                    behandling_id,
                    ekstern_referanse, 
                    oppgave_status, 
                    oppgave_kode,
                    opprettet,
                    sist_endret,
                    beslutter,
                    ferdigstilt_tidspunkt,
                    ferdigstilt_saksbehandler,
                    ferdigstilt_enhet,
                    frist
                ) values (
                    :behandling_id,
                    :ekstern_referanse,
                    :oppgave_status,
                    :oppgave_kode,
                    :opprettet,
                    :sist_endret,
                    :beslutter,
                    :ferdigstilt_tidspunkt,
                    :ferdigstilt_saksbehandler,
                    :ferdigstilt_enhet,
                    :frist
                )
             """, mapOf(
                "behandling_id" to behandlingId,
                "ekstern_referanse" to oppgave.eksternReferanse,
                "oppgave_status" to oppgave.oppgaveStatus.kode,
                "oppgave_kode" to oppgave.oppgaveKode,
                "opprettet" to oppgave.opprettet,
                "sist_endret" to oppgave.sistEndret,
                "beslutter" to oppgave.erBeslutter,
                "ferdigstilt_tidspunkt" to oppgave.ferdigstilt?.tidspunkt,
                "ferdigstilt_saksbehandler" to oppgave.ferdigstilt?.ansvarligSaksbehandlerIdent,
                "ferdigstilt_enhet" to oppgave.ferdigstilt?.behandlendeEnhet,
                "frist" to oppgave.frist,
            )
        )
    }

    private fun update(oppgave: OppgaveV2): UpdateQueryAction {
        return queryOf(
            """
                UPDATE oppgave_v2 SET
                    oppgave_status = :oppgave_status,
                    sist_endret = :sist_endret,
                    beslutter = :beslutter,
                    ferdigstilt_tidspunkt = :ferdigstilt_tidspunkt, 
                    ferdigstilt_saksbehandler = :ferdigstilt_saksbehandler, 
                    ferdigstilt_enhet = :ferdigstilt_enhet,
                    frist = :frist
                WHERE id = :id
             """, mapOf(
                "id" to oppgave.id,
                "oppgave_status" to oppgave.oppgaveStatus.kode,
                "sist_endret" to oppgave.sistEndret,
                "beslutter" to oppgave.erBeslutter,
                "ferdigstilt_tidspunkt" to oppgave.ferdigstilt?.tidspunkt,
                "ferdigstilt_saksbehandler" to oppgave.ferdigstilt?.ansvarligSaksbehandlerIdent,
                "ferdigstilt_enhet" to oppgave.ferdigstilt?.behandlendeEnhet,
                "frist" to oppgave.frist,
            )
        ).asUpdate
    }


}