package no.nav.k9.domene.lager.oppgave.v2

import kotliquery.*
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.tjenester.innsikt.Databasekall
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class OppgaveRepositoryV2(
    private val dataSource: DataSource
) {

    fun hentBehandling(eksternReferanse: String, tx: TransactionalSession? = null): Behandling? {
        return tx?.run {
            val deloppgaver = run(hentOppgaver(eksternReferanse))
            run(hentBehandling(eksternReferanse, deloppgaver))
        } ?: using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                val deloppgaver = tx.run(hentOppgaver(eksternReferanse))
                tx.run(hentBehandling(eksternReferanse, deloppgaver))
            }
        }
    }

    private fun hentBehandling(
        eksternReferanse: String,
        deloppgaver: Collection<Deloppgave>
    ): NullableResultQueryAction<Behandling> {
        return queryOf(
            """
                        select
                            id,
                            fagsystem,
                            ytelse_type,
                            behandling_type,
                            soekers_id,
                            ferdigstilt_tidspunkt,
                            kode6,
                            skjermet
                        from behandling 
                        WHERE ekstern_referanse = :ekstern_referanse
                    """,
            mapOf("ekstern_referanse" to eksternReferanse)
        ).map { row -> row.tilBehandling(eksternReferanse, deloppgaver) }.asSingle
    }

    private fun Row.tilBehandling(eksternReferanse: String, deloppgaver: Collection<Deloppgave>): Behandling {
        return Behandling(
            id = UUID.fromString(string("id")),
            eksternReferanse = eksternReferanse,
            oppgaver = deloppgaver.toMutableSet(),
            fagsystem = Fagsystem.fraKode(string("fagsystem")),
            ytelseType = FagsakYtelseType.fraKode(string("ytelse_type")),
            behandlingType = stringOrNull("behandling_type"),
            søkersId = Ident(string("soekers_id"), Ident.IdType.AKTØRID),
            kode6 = boolean("kode6"),
            skjermet = boolean("skjermet"),
        ).also {
            it.ferdigstilt = localDateTimeOrNull("ferdigstilt_tidspunkt")
        }
    }

    private fun hentOppgaver(eksternReferanse: String): ListResultQueryAction<Deloppgave> {
        return queryOf(
            """
                        select
                            id,
                            ekstern_referanse, 
                            oppgave_status, 
                            oppgave_kode, 
                            opprettet,
                            beslutter,
                            sist_endret,
                            ferdigstilt_tidspunkt,
                            ferdigstilt_saksbehandler,
                            ferdigstilt_enhet,
                            frist
                        from deloppgave 
                        WHERE ekstern_referanse = :ekstern_referanse
                    """,
            mapOf(
                    "ekstern_referanse" to eksternReferanse,
                )
            ).map { row ->
                Deloppgave(
                    id = UUID.fromString(row.string("id")),
                    eksternReferanse = eksternReferanse,
                    opprettet = row.localDateTime("opprettet"),
                    sistEndret = row.localDateTime("sist_endret"),
                    oppgaveStatus = OppgaveStatus.valueOf(row.string("oppgave_status")),
                    oppgaveKode = row.string("oppgave_kode"),
                    erBeslutter = row.boolean("beslutter"),
                    frist = row.localDateTimeOrNull("frist")
                ).also {
                    row.localDateTimeOrNull("ferdigstilt_tidspunkt")?.let { ferdigstiltTidspunkt ->
                        it.ferdigstilt = Deloppgave.Ferdigstilt(
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
        lagreBehandling(behandling, tx)
        behandling.oppgaver().forEach { oppgave ->
            lagre(behandling.id, oppgave, tx)
        }
    }

    private fun lagreBehandling(behandling: Behandling, tx: TransactionalSession) {
        tx.run(
            queryOf(
                """
                    insert into behandling (
                        id,
                        ekstern_referanse,
                        fagsystem,
                        ytelse_type,
                        behandling_type,
                        soekers_id,
                        ferdigstilt_tidspunkt,
                        kode6,
                        skjermet
                    ) VALUES (
                        :id,
                        :ekstern_referanse,
                        :fagsystem,
                        :ytelse_type,
                        :behandling_type,
                        :soekers_id,
                        :ferdigstilt_tidspunkt,
                        :kode6,
                        :skjermet
                    )  on conflict (id) do update set
                        ekstern_referanse = :ekstern_referanse, 
                        fagsystem = :fagsystem,
                        ytelse_type = :ytelse_type,
                        behandling_type = :behandling_type,
                        soekers_id = :soekers_id,
                        kode6 = :kode6,
                        skjermet = :skjermet
                        """, mapOf(
                    "id" to behandling.id.toString(),
                    "ekstern_referanse" to behandling.eksternReferanse,
                    "fagsystem" to behandling.fagsystem.kode,
                    "ytelse_type" to behandling.ytelseType.kode,
                    "behandling_type" to behandling.behandlingType,
                    "soekers_id" to behandling.søkersId?.id,
                    "ferdigstilt_tidspunkt" to behandling.ferdigstilt,
                    "kode6" to behandling.kode6,
                    "skjermet" to behandling.skjermet
                )
            ).asUpdate
        )
    }


    private fun lagre(behandlingId: UUID, oppgave: Deloppgave, tx: TransactionalSession) {
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }
            .increment()

        tx.run(
            queryOf(
                """
                    insert into deloppgave (
                        id,
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
                        :id,
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
                    ) on conflict (id) do update set 
                        ekstern_referanse = :ekstern_referanse, 
                        oppgave_status = :oppgave_status, 
                        oppgave_kode = :oppgave_kode, 
                        opprettet = :opprettet,
                        sist_endret = :sist_endret, 
                        beslutter = :beslutter,
                        ferdigstilt_tidspunkt = :ferdigstilt_tidspunkt,
                        ferdigstilt_saksbehandler = :ferdigstilt_saksbehandler,
                        ferdigstilt_enhet = :ferdigstilt_enhet,
                        frist = :frist
                 """, mapOf(
                    "id" to oppgave.id.toString(),
                    "behandling_id" to behandlingId.toString(),
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
            ).asUpdate
        )
    }
}