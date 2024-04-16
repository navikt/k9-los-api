package no.nav.k9.los.domene.lager.oppgave.v2

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Query
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.tjenester.innsikt.Databasekall
import no.nav.k9.los.tjenester.saksbehandler.merknad.Merknad
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.util.*
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
        val merknad = tx.run(hentMerknaderQuery(eksternReferanse))
            .also { check(it.size <= 1) { "Behandling kan bare ha en aktiv merknad. Fant ${it.size}" }}
            .firstOrNull()
        return tx.run(hentBehandling(eksternReferanse, oppgaver, merknad))
    }

    private fun hentBehandling(
        eksternReferanse: String,
        oppgaver: Collection<OppgaveV2>,
        merknad: Merknad?
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
        ).map { row -> row.tilBehandling(eksternReferanse, oppgaver, merknad) }.asSingle
    }

    private fun Row.tilBehandling(
        eksternReferanse: String,
        oppgaver: Collection<OppgaveV2>,
        merknad: Merknad?
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
            merknad = merknad,
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

    fun hentMerknader(eksternReferanse: String, inkluderSlettet: Boolean = false): List<Merknad> {
        return using(sessionOf(dataSource)) { it.run(hentMerknaderQuery(eksternReferanse, inkluderSlettet)) }
    }

    fun hentMerknader(eksternReferanse: String, inkluderSlettet: Boolean = false, tx: TransactionalSession): List<Merknad> {
        return tx.run(hentMerknaderQuery(eksternReferanse, inkluderSlettet))
    }

    private fun hentMerknaderQuery(eksternReferanse: String, inkluderSlettet: Boolean = false) : ListResultQueryAction<Merknad> {
            return queryOf(
                """
                        select
                            id,
                            merknad_koder,
                            oppgave_ider,
                            oppgave_koder,
                            fritekst,
                            saksbehandler,
                            opprettet,
                            sist_endret,
                            slettet
                        from merknad 
                        WHERE ekstern_referanse = :ekstern_referanse 
                        and slettet = :slettet
                    """,
                mapOf(
                    "ekstern_referanse" to eksternReferanse,
                    "slettet" to inkluderSlettet
                )
            ).map { row -> Merknad(
                oppgaveKoder = LosObjectMapper.instance.readValue(row.string("oppgave_koder")),
                oppgaveIder = LosObjectMapper.instance.readValue(row.string("oppgave_ider")),
                saksbehandler = row.stringOrNull("saksbehandler"),
                opprettet = row.localDateTime("opprettet"),
                sistEndret = row.localDateTimeOrNull("sist_endret"),
                slettet = row.boolean("slettet")
            ).apply {
                id = row.long("id")
                merknadKoder = LosObjectMapper.instance.readValue(row.string("merknad_koder"))
                fritekst = row.stringOrNull("fritekst")
            }}.asList
    }

    data class EksternReferanseMerknad (val eksternReferanse: String, val merknad: Merknad)

    fun hentAlleMerknader(): List<EksternReferanseMerknad> {
        return using(sessionOf(dataSource)) { it.run(hentAlleMerknaderQuery()) }
    }

    fun hentAlleMerknader(tx: TransactionalSession): List<EksternReferanseMerknad> {
        return tx.run(hentAlleMerknaderQuery())
    }
    private fun hentAlleMerknaderQuery() : ListResultQueryAction<EksternReferanseMerknad> {
        return queryOf(
            """
                        select
                            ekstern_referanse,
                            id,
                            merknad_koder,
                            oppgave_ider,
                            oppgave_koder,
                            fritekst,
                            saksbehandler,
                            opprettet,
                            sist_endret,
                            slettet
                        from merknad 
                        WHERE not slettet
                    """,
            mapOf()
        ).map { row -> EksternReferanseMerknad(
            eksternReferanse = row.string("ekstern_referanse"),
            merknad = Merknad(
                oppgaveKoder = LosObjectMapper.instance.readValue(row.string("oppgave_koder")),
                oppgaveIder = LosObjectMapper.instance.readValue(row.string("oppgave_ider")),
                saksbehandler = row.stringOrNull("saksbehandler"),
                opprettet = row.localDateTime("opprettet"),
                sistEndret = row.localDateTimeOrNull("sist_endret"),
                slettet = row.boolean("slettet")
            ).apply {
                id = row.long("id")
                merknadKoder = LosObjectMapper.instance.readValue(row.string("merknad_koder"))
                fritekst = row.stringOrNull("fritekst")
            }
        )}.asList
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

            behandling.merknad?.let { merknad ->
            merknad.id?.also { tx.run(update(merknad)) }
                ?: tx.updateAndReturnGeneratedKey(insert(dbId!!, behandling.eksternReferanse, merknad))?.also { merknad.id = it }
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
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }.increment()

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
        Databasekall.map.computeIfAbsent(object {}.javaClass.name + object {}.javaClass.enclosingMethod.name) { LongAdder() }.increment()

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


    private fun update(merknad: Merknad): UpdateQueryAction {
        return queryOf(
            """
                UPDATE merknad SET
                    sist_endret = :sist_endret,
                    fritekst = :fritekst,
                    merknad_koder = :merknad_koder :: jsonb,
                    slettet = :slettet
                WHERE id = :id
            """, mapOf(
                "id" to merknad.id,
                "fritekst" to merknad.fritekst,
                "sist_endret" to merknad.sistEndret,
                "merknad_koder" to LosObjectMapper.instance.writeValueAsString(merknad.merknadKoder),
                "slettet" to merknad.slettet
            )
        ).asUpdate
    }

    private fun insert(behandlingId: Long, eksternReferanse: String, merknad: Merknad): Query {
        return queryOf(
            """
                insert into merknad (
                    behandling_id,
                    ekstern_referanse,
                    merknad_koder,
                    oppgave_koder,
                    oppgave_ider,
                    fritekst,
                    saksbehandler,
                    opprettet,
                    sist_endret
                ) VALUES (
                    :behandling_id,
                    :ekstern_referanse,
                    :merknad_koder :: jsonb,
                    :oppgave_koder :: jsonb,
                    :oppgave_ider :: jsonb,
                    :fritekst,
                    :saksbehandler,
                    :opprettet,
                    :sist_endret
                )
                """, mapOf(
                "behandling_id" to behandlingId,
                "ekstern_referanse" to eksternReferanse,
                "merknad_koder" to LosObjectMapper.instance.writeValueAsString(merknad.merknadKoder),
                "oppgave_koder" to LosObjectMapper.instance.writeValueAsString(merknad.oppgaveKoder),
                "oppgave_ider" to LosObjectMapper.instance.writeValueAsString(merknad.oppgaveIder),
                "fritekst" to merknad.fritekst,
                "saksbehandler" to merknad.saksbehandler,
                "opprettet" to merknad.opprettet,
                "sist_endret" to merknad.sistEndret,
            )
        )
    }
}