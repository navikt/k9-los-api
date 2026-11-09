package no.nav.k9.los.tjenester.mock

import kotliquery.queryOf
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter as K9EventTilOppgaveAdapter
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveV3Repository
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.typer.Periode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Registrerer bare egne oppgaver. Ukjente ID-er gir aldri tilgang til sletting. */
internal class TestdataTjeneste(
    private val transactionalManager: TransactionalManager,
    private val eventRepository: EventRepository,
    private val adapter: K9EventTilOppgaveAdapter,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveV3Repository: OppgaveV3Repository,
) {
    private val oppgaver = mutableSetOf<UUID>()

    fun erKlar(): Boolean = transactionalManager.transaction { tx ->
        tx.run(
            queryOf("select exists(select 1 from saksbehandler where navident = 'Z123456') as klar")
                .map { it.boolean("klar") }.asSingle
        ) == true
    }

    @Synchronized
    fun opprettOppgave(): Testoppgave {
        val id = UUID.randomUUID()
        val event = lagEvent(id)
        transactionalManager.transaction { tx ->
            val nøkkel = eventRepository.lagre(
                Fagsystem.K9SAK, id.toString(), event.eventTid.toString(),
                LosObjectMapper.instance.writeValueAsString(event), tx,
            )
            adapter.oppdaterOppgaveForEksternId(nøkkel, tx)
            val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternId(tx, Områder.K9, id.toString())
            check(oppgave.status == Oppgavestatus.AAPEN) { "Testoppgaven ble ikke klar for reservering" }
        }
        oppgaver.add(id)
        return Testoppgave(id, event.saksnummer, id.toString(), "k9sak")
    }

    @Synchronized
    fun slettOppgave(id: UUID): Boolean {
        if (id !in oppgaver) return false
        transactionalManager.transaction { tx ->
            val eventId = eventRepository.hentOgLåsEventnøkkel(Fagsystem.K9SAK, id.toString(), tx)
            val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternId(tx, Områder.K9, id.toString())
            val params = mapOf(
                "id" to id.toString(),
                "eventId" to eventId,
                "reservasjonsnokkel" to oppgave.reservasjonsnøkkel,
            )
            listOf(
                """
                    delete from reservasjon_v3_endring
                    where annullert_reservasjon_id in (
                        select id from reservasjon_v3 where reservasjonsnokkel = :reservasjonsnokkel
                        and omrade_id = (select id from omrade where ekstern_id = 'K9')
                    ) or ny_reservasjon_id in (
                        select id from reservasjon_v3 where reservasjonsnokkel = :reservasjonsnokkel
                        and omrade_id = (select id from omrade where ekstern_id = 'K9')
                    )
                """.trimIndent(),
                """
                    delete from reservasjon_v3 where reservasjonsnokkel = :reservasjonsnokkel
                    and omrade_id = (select id from omrade where ekstern_id = 'K9')
                """.trimIndent(),
                """
                    delete from siste_oppgaver where oppgave_ekstern_id = :id
                    and oppgavetype_id = (select id from oppgavetype where ekstern_id = 'k9sak'
                        and omrade_id = (select id from omrade where ekstern_id = 'K9'))
                """.trimIndent(),
                "delete from oppgave_pep_cache where ekstern_id = :id and omrade = 'K9'",
                "delete from oppgave_v3_dvh_pending where ekstern_id = :id and oppgavetype_ekstern_id = 'k9sak'",
                """
                    delete from oppgavefelt_verdi_part where oppgave_id in (
                        select id from oppgave_id_part
                        where oppgave_ekstern_id = :id and oppgavetype_ekstern_id = 'k9sak'
                    )
                """.trimIndent(),
                "delete from oppgave_v3_part where oppgave_ekstern_id = :id and oppgavetype_ekstern_id = 'k9sak'",
                "delete from oppgave_id_part where oppgave_ekstern_id = :id and oppgavetype_ekstern_id = 'k9sak'",
                "delete from event_historikkvask_bestilt where event_nokkel_id = :eventId",
                "delete from event where event_nokkel_id = :eventId",
                "delete from event_nokkel where id = :eventId",
            ).forEach { sql -> tx.run(queryOf(sql, params).asUpdate) }
            oppgaveV3Repository.slettOppgave(OppgaveNøkkelDto(id.toString(), "k9sak", Områder.K9), tx)
        }
        return oppgaver.remove(id)
    }

    private fun lagEvent(id: UUID): K9SakEventDto {
        val now = LocalDateTime.now().withNano(0)
        val aktørId = "9" + id.toString().replace("-", "").take(12).map { it.digitToInt(16) % 10 }.joinToString("")
        return K9SakEventDto(
            eksternId = id,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "E2E" + id.toString().replace("-", "").take(12).uppercase(),
            aktørId = aktørId,
            pleietrengendeAktørId = aktørId,
            behandlingId = 1,
            vedtaksdato = null,
            behandlingstidFrist = null,
            eventTid = now,
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.KONTROLLER_FAKTA.kode,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            behandlendeEnhet = "3450",
            opprettetBehandling = now.minusDays(1),
            eldsteDatoMedEndringFraSøker = now.minusDays(1),
            fagsakPeriode = Periode(LocalDate.now().minusDays(7), LocalDate.now()),
            aksjonspunktTilstander = listOf(
                AksjonspunktTilstandDto(
                    AksjonspunktKodeDefinisjon.KONTROLLER_LEGEERKLÆRING_KODE,
                    AksjonspunktStatus.OPPRETTET, null, null, now, now, now,
                ),
            ),
        )
    }
}
