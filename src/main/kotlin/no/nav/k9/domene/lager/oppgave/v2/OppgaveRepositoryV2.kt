package no.nav.k9.domene.lager.oppgave.v2

import kotliquery.TransactionalSession
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.tjenester.innsikt.Databasekall
import java.util.*
import java.util.concurrent.atomic.LongAdder
import javax.sql.DataSource

class OppgaveRepositoryV2(
    private val dataSource: DataSource
) {

    fun hentAlleAktiveOppgaverForFagsystemGruppertPrReferanse(k9SAK: Fagsystem): Map<String, Behandling> {
        TODO("Not yet implemented")
    }

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
        deloppgaver: Collection<OppgaveV2>
    ): NullableResultQueryAction<Behandling> {
        return queryOf(
            """
                        select
                            id,
                            fagsystem,
                            ytelse_type,
                            soekers_id,
                            ferdigstilt_tidspunkt,
                            kode6,
                            skjermet
                        from behandling 
                        WHERE ekstern_referanse = :ekstern_referanse
                    """,
            mapOf("ekstern_referanse" to eksternReferanse.toString())
        ).map { row ->
                Behandling(
                    id = UUID.fromString(row.string("id")),
                    eksternReferanse = eksternReferanse.toString(),
                    oppgaver = deloppgaver.toMutableSet(),
                    fagsystem = Fagsystem.fraKode(row.string("fagsystem")),
                    ytelseType = FagsakYtelseType.fraKode(row.string("ytelse_type")),
                    søkersId = Ident(row.string("soekers_id"), Ident.IdType.AKTØRID),
                    kode6 = row.boolean("kode6"),
                    skjermet = row.boolean("skjermet"),
                ).also { it.ferdigstilt = row.localDateTimeOrNull("ferdigstilt_tidspunkt") }
            }.asSingle
    }

    private fun hentOppgaver(eksternReferanse: String): ListResultQueryAction<OppgaveV2> {
        return queryOf(
            """
                        select
                            id,
                            ekstern_referanse, 
                            oppgave_status, 
                            oppgave_kode, 
                            opprettet,
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
                OppgaveV2(
                    id = UUID.fromString(row.string("id")),
                    eksternReferanse = eksternReferanse,
                    opprettet = row.localDateTime("opprettet"),
                    sistEndret = row.localDateTime("sist_endret"),
                    oppgaveStatus = OppgaveStatus.valueOf(row.string("oppgave_status")),
                    oppgaveKode = row.stringOrNull("oppgave_kode"),
                ).also {
                    row.localDateTimeOrNull("ferdigstilt_tidspunkt")?.let { ferdigstiltTidspunkt ->
                        it.ferdigstill(
                            tidspunkt = ferdigstiltTidspunkt,
                            ansvarligSaksbehandler = row.stringOrNull("ferdigstilt_saksbehandler"),
                            behandlendeEnhet = row.stringOrNull("ferdigstilt_enhet")
                        )
                    }
                }
            }.asList
    }

    fun lagre(behandling: Behandling) {
        behandling.oppgaver().run {
            using(sessionOf(dataSource)) {
                it.transaction { tx ->
                    lagreBehandling(behandling, tx)
                    forEach { oppgave ->
                        lagre(behandling.id, oppgave, tx)
                    }
                }
            }
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
                        soekers_id,
                        ferdigstilt_tidspunkt,
                        kode6,
                        skjermet
                    ) VALUES (
                        :id,
                        :ekstern_referanse,
                        :fagsystem,
                        :ytelse_type,
                        :soekers_id,
                        :ferdigstilt_tidspunkt,
                        :kode6,
                        :skjermet
                    )  on conflict (id) do update set
                        ekstern_referanse = :ekstern_referanse, 
                        fagsystem = :fagsystem,
                        ytelse_type = :ytelse_type,
                        soekers_id = :soekers_id,
                        kode6 = :kode6,
                        skjermet = :skjermet
                        """, mapOf(
                    "id" to behandling.id.toString(),
                    "ekstern_referanse" to behandling.eksternReferanse,
                    "fagsystem" to behandling.fagsystem.kode,
                    "ytelse_type" to behandling.ytelseType.kode,
                    "soekers_id" to behandling.søkersId?.id,
                    "ferdigstilt_tidspunkt" to behandling.ferdigstilt,
                    "kode6" to behandling.kode6,
                    "skjermet" to behandling.skjermet
                )
            ).asUpdate
        )
    }


    private fun lagre(behandlingId: UUID, oppgave: OppgaveV2, tx: TransactionalSession) {
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
                    "ferdigstilt_tidspunkt" to oppgave.ferdistilt?.tidspunkt,
                    "ferdigstilt_saksbehandler" to oppgave.ferdistilt?.ansvarligSaksbehandlerIdent,
                    "ferdigstilt_enhet" to oppgave.ferdistilt?.behandlendeEnhet,
                    "frist" to oppgave.frist,
                )
            ).asUpdate
        )
    }
}