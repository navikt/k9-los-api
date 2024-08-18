package no.nav.k9.los.eventhandler

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.nyoppgavestyring.ko.*
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.query.Avgrensning
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.utils.OpentelemetrySpanUtil
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

    fun refreshK9(hendelser: List<KøpåvirkendeHendelse>) {
        OpentelemetrySpanUtil.span("refreshk9") {
            transactionalManager.transaction { tx ->
                refreshK9(tx, hendelser)
            }
        }
    }

    private fun refreshK9(tx: TransactionalSession, hendelser: List<KøpåvirkendeHendelse>) {

        val aktuelleHendelser = hendelser
            .filterNot { it is OppgaveHendelseMottatt && it.fagsystem != Fagsystem.K9SAK  }
            .filterNot { it is KødefinisjonSlettet }
            .filterNot { it is ReservasjonEndret }

        if (aktuelleHendelser.isEmpty()){
            log.info("Fikk ${hendelser.size}, ingen var aktuelle for å refreshe k9sak")
            return;
        }

        val oppfriskerFraAlleKøer : Boolean
        val behandlinger  : Set<UUID> = if (aktuelleHendelser.all { it is Kødefinisjon }) {
            oppfriskerFraAlleKøer = false
            log.info("Fikk ${aktuelleHendelser.size} aktuelle hendelser, alle var relatert til kødefinisjoner")
            behandlingerTilOppfriskningForKøer(tx, aktuelleHendelser.map { it as Kødefinisjon }.map { it.køId }.distinct(), antallPrKø)
        } else {
            oppfriskerFraAlleKøer = true
            //TODO der er fint mulig å refreshe bare de køene som er påvirket av hendelsene.
            // Vi bør se på muligheten for dette når vi har fått erfaring fra prod med hvor mye
            // forsinkelse og ressursbruk det kommer fra å refreshe alle køer hver gang
            behandlingerTilOppfriskning(tx, antallPrKø)
        }

        DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "k9SakService") {
            runBlocking {
                k9SakService.refreshBehandlinger(behandlinger)
            }
        }

        if (oppfriskerFraAlleKøer) {
            //ta litt pause for å ikke lage unødvendig høy last
            //TODO tilpasse når vi har fått erfaring fra prod
            //kan fjernes dersom vi får på plass å bare hente oppgaver fra køer som er direkte påvirket
            Thread.sleep(15000)
        }
    }

    fun behandlingerTilOppfriskning(antallPrKø: Int) : Set<UUID> {
        return transactionalManager.transaction {
            tx -> behandlingerTilOppfriskning(tx, antallPrKø)
        }
    }

    fun behandlingerTilOppfriskning(tx: TransactionalSession, antallPrKø: Int) : Set<UUID> {
        return DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "alle") {
            val alleKøer = oppgaveKoRepository.hentListe(false) + oppgaveKoRepository.hentListe(true)
            val behandlinger = behandlingerTilOppfriskning(tx, alleKøer, antallPrKø)
            log.info("Hentet ${behandlinger.size} oppgaver fra ${alleKøer.size} køer")
            behandlinger
        }
    }

    fun behandlingerTilOppfriskningForKøer(tx: TransactionalSession, køId: List<Long>, antallPrKø: Int) : Set<UUID>{
        return DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", køId.size.toString()) {
            val køer = køId.map { oppgaveKoRepository.hent(it) }
            behandlingerTilOppfriskning(tx, køer, antallPrKø)
        }
    }

    fun behandlingerTilOppfriskning(tx: TransactionalSession, køer: List<OppgaveKo>, antallPrKø: Int) : Set<UUID> {
        return DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "hentOgRefresh") {
            val førsteOppgaveIder = mutableSetOf<AktivOppgaveId>()
            for (kø in køer) {
                DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "hentFørsteOppgaverIterativt") {
                    val førsteOppgaverIKøen = oppgaveQueryService.queryForOppgaveId(
                        tx,
                        QueryRequest(
                            kø.oppgaveQuery,
                            fjernReserverte = true,
                            Avgrensning.maxAntall(antall = antallPrKø.toLong())
                        )
                    )
                    førsteOppgaveIder.addAll(førsteOppgaverIKøen)
                }
            }
            DetaljerMetrikker.time("RefreshK9V3", "refreshForKøer", "parsaker") {
                aktivOppgaveRepository.hentK9sakParsakOppgaver(tx, førsteOppgaveIder).map { UUID.fromString(it.eksternId)}.toSet()
            }
        }
    }

    companion object {
        val log = LoggerFactory.getLogger("RefreshK9v3Tjeneste")
    }

}