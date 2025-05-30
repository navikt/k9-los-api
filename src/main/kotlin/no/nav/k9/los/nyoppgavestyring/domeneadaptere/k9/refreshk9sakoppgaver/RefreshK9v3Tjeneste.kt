package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.DetaljerMetrikker
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.IK9SakService
import no.nav.k9.los.nyoppgavestyring.ko.*
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.query.Avgrensning
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory
import java.util.*

class RefreshK9v3Tjeneste(
    val k9SakService: IK9SakService,
    val oppgaveQueryService: OppgaveQueryService,
    val aktivOppgaveRepository: AktivOppgaveRepository,
    val oppgaveKoRepository: OppgaveKoRepository,
    val transactionalManager: TransactionalManager,
    val antallPrKø: Int = 10
) {

    enum class RefreshUtført {
        ALLE_KØER,
        NOEN_KØER,
        INGENTING
    }

    @WithSpan
    fun refreshK9(hendelser: List<KøpåvirkendeHendelse>): RefreshUtført {
        return transactionalManager.transaction { tx ->
            refreshK9(tx, hendelser)
        }
    }

    private fun refreshK9(tx: TransactionalSession, hendelser: List<KøpåvirkendeHendelse>): RefreshUtført {

        val aktuelleHendelser = hendelser
            .filterNot { it is OppgaveHendelseMottatt && it.fagsystem != Fagsystem.K9SAK }
            .filterNot { it is KødefinisjonSlettet }
            .filterNot { it is ReservasjonEndret }
            .filterNot { it is Kødefinisjon && hendelser.any { hendelse -> hendelse is KødefinisjonSlettet && hendelse.køId == it.køId } }

        if (aktuelleHendelser.isEmpty()) {
            log.info("Fikk ${hendelser.size}, ingen var aktuelle for å refreshe k9sak")
            return RefreshUtført.INGENTING;
        }

        val oppfriskerFraAlleKøer: Boolean
        val behandlinger: Set<UUID> = if (aktuelleHendelser.all { it is Kødefinisjon }) {
            oppfriskerFraAlleKøer = false
            log.info("Fikk ${aktuelleHendelser.size} aktuelle hendelser, alle var relatert til kødefinisjoner")
            behandlingerTilOppfriskningForKøer(
                tx,
                aktuelleHendelser.map { it as Kødefinisjon }.map { it.køId }.distinct(),
                antallPrKø
            )
        } else {
            oppfriskerFraAlleKøer = true
            //TODO der er fint mulig å refreshe bare de køene som er påvirket av hendelsene.
            // Vi bør se på muligheten for dette når vi har fått erfaring fra prod med hvor mye
            // forsinkelse og ressursbruk det kommer fra å refreshe alle køer hver gang
            behandlingerTilOppfriskning(tx, antallPrKø)
        }

        DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "k9SakService") {
            runBlocking(Span.current().asContextElement()) {
                k9SakService.refreshBehandlinger(behandlinger)
            }
        }

        return if (oppfriskerFraAlleKøer) {
            RefreshUtført.ALLE_KØER
        } else {
            RefreshUtført.NOEN_KØER
        }
    }

    fun behandlingerTilOppfriskning(antallPrKø: Int): Set<UUID> {
        return transactionalManager.transaction { tx ->
            behandlingerTilOppfriskning(tx, antallPrKø)
        }
    }

    @WithSpan
    fun behandlingerTilOppfriskning(tx: TransactionalSession, antallPrKø: Int): Set<UUID> {
        return DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "alle") {
            val alleKøer = oppgaveKoRepository.hentListe(skjermet = false, medSaksbehandlere = false) +
                    oppgaveKoRepository.hentListe(skjermet = true, medSaksbehandlere = false)
            val behandlinger = behandlingerTilOppfriskning(tx, alleKøer, antallPrKø)
            log.info("Hentet ${behandlinger.size} oppgaver fra ${alleKøer.size} køer")
            behandlinger
        }
    }

    @WithSpan
    fun behandlingerTilOppfriskningForKøer(tx: TransactionalSession, køId: List<Long>, antallPrKø: Int): Set<UUID> {
        return DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", køId.size.toString()) {
            val køer = køId.map { oppgaveKoRepository.hentInkluderKode6(it) }
            behandlingerTilOppfriskning(tx, køer, antallPrKø)
        }
    }

    fun behandlingerTilOppfriskning(tx: TransactionalSession, køer: List<OppgaveKo>, antallPrKø: Int): Set<UUID> {
        return DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "hentOgRefresh") {
            val førsteOppgaver = mutableSetOf<Oppgave>()
            for (kø in køer) {
                DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "hentFørsteOppgaverIterativt") {
                    OpentelemetrySpanUtil.span(
                        "hentFørsteUreserverteFraKø",
                        mapOf("køId" to kø.id.toString(), "antall" to antallPrKø.toString())
                    ) {
                        val førsteOppgaverIKøen = oppgaveQueryService.queryForOppgave(
                            tx,
                            QueryRequest(
                                kø.oppgaveQuery,
                                fjernReserverte = true,
                                Avgrensning.maxAntall(antall = antallPrKø.toLong())
                            )
                        )
                        førsteOppgaver.addAll(førsteOppgaverIKøen)
                    }
                }
            }
            DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "parsaker") {
                aktivOppgaveRepository.hentK9sakParsakOppgaver(tx, førsteOppgaver)
                    .map { UUID.fromString(it.eksternId) }.toSet()
            }
        }
    }

    companion object {
        val log = LoggerFactory.getLogger("RefreshK9v3Tjeneste")
    }

}