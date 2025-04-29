package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.EpostDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.saksliste.OppgavekøDto
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SaksbehandlerDto
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SorteringDto

class SaksbehandlerAdminTjeneste(
    private val pepClient: IPepClient,
    private val transactionalManager: TransactionalManager,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val oppgaveKøV3Repository: OppgaveKoRepository,

    private val oppgaveKøRepository: OppgaveKøRepository,
    private val oppgaveTjeneste: OppgaveTjeneste,
) {

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    suspend fun søkSaksbehandler(epostDto: EpostDto): Saksbehandler {
        var saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(epostDto.epost)
        if (saksbehandler == null) {
            saksbehandler = Saksbehandler(
                null, null, null, epostDto.epost, mutableSetOf(), null
            )
            saksbehandlerRepository.addSaksbehandler(saksbehandler)
        }
        return saksbehandler
    }

    suspend fun leggTilSaksbehandler(epost: String) {
        if (saksbehandlerRepository.finnSaksbehandlerMedEpost(epost) != null) {
            throw IllegalStateException("Saksbehandler finnes fra før")
        }
        // lagrer med tomme verdier, disse blir populert etter at saksbehandleren har logget seg inn
        val saksbehandler = Saksbehandler(null, null, null, epost, mutableSetOf(), null)
        saksbehandlerRepository.addSaksbehandler(saksbehandler)
    }

    suspend fun slettSaksbehandler(
        epost: String,
    ) {
        val skjermet = pepClient.harTilgangTilKode6()

        transactionalManager.transaction { tx ->
            // V3-modellen: Sletter køer saksbehandler er med i
            oppgaveKøV3Repository.hentKoerMedOppgittSaksbehandler(tx, epost, skjermet, true).forEach { kø ->
                oppgaveKøV3Repository.endre(tx, kø.copy(saksbehandlere = kø.saksbehandlere - epost), skjermet)
            }

            // Sletter fra saksbehandler-tabellen
            saksbehandlerRepository.slettSaksbehandler(
                tx,
                epost,
                skjermet
            )
        }

        // V1-modellen: Sletter køer saksbehandler er med i. (Lager sin egen transaksjon.)
        oppgaveKøRepository.hentAlle().forEach { t: OppgaveKø ->
            oppgaveKøRepository.lagre(t.id) { oppgaveKø ->
                oppgaveKø!!.saksbehandlere =
                    oppgaveKø.saksbehandlere.filter { it.epost != epost }
                        .toMutableList()
                oppgaveKø
            }
        }
    }

    suspend fun hentSaksbehandlere(): List<SaksbehandlerDto> {
        val saksbehandlersKoer = hentSaksbehandlersOppgavekoer()
        return saksbehandlersKoer.entries.map {
            SaksbehandlerDto(
                id = it.key.id,
                brukerIdent = it.key.brukerIdent,
                navn = it.key.navn,
                epost = it.key.epost,
                enhet = it.key.enhet,
                oppgavekoer = it.value.map { ko -> ko.navn })
        }.sortedBy { it.navn }
    }

    private suspend fun hentSaksbehandlersOppgavekoer(): Map<Saksbehandler, List<OppgavekøDto>> {
        val koer = oppgaveTjeneste.hentOppgaveKøer()
        val saksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()
        val map = mutableMapOf<Saksbehandler, List<OppgavekøDto>>()
        for (saksbehandler in saksbehandlere) {
            map[saksbehandler] = koer.filter { oppgaveKø ->
                oppgaveKø.saksbehandlere
                    .any { s -> s.epost == saksbehandler.epost }
            }
                .map { oppgaveKø ->
                    OppgavekøDto(
                        id = oppgaveKø.id,
                        navn = oppgaveKø.navn,
                        behandlingTyper = oppgaveKø.filtreringBehandlingTyper,
                        fagsakYtelseTyper = oppgaveKø.filtreringYtelseTyper,
                        saksbehandlere = oppgaveKø.saksbehandlere,
                        antallBehandlinger = oppgaveKø.oppgaverOgDatoer.size, //TODO dette feltet i DTO-en brukers annet sted til å sende antall inkludert reserverte, her er det ekskludert reserverte
                        antallUreserverteOppgaver = oppgaveKø.oppgaverOgDatoer.size,
                        sistEndret = oppgaveKø.sistEndret,
                        skjermet = oppgaveKø.skjermet,
                        sortering = SorteringDto(oppgaveKø.sortering, oppgaveKø.fomDato, oppgaveKø.tomDato),
                        andreKriterier = oppgaveKø.filtreringAndreKriterierType,
                        kriterier = oppgaveKø.lagKriterier()
                    )
                }
        }
        return map
    }
}