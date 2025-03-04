package no.nav.k9.los.tjenester.avdelingsleder

import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.*
import no.nav.k9.los.tjenester.avdelingsleder.reservasjoner.ReservasjonDto
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SaksbehandlerDto
import java.util.*

class AvdelingslederTjeneste(
    private val transactionalManager: TransactionalManager,
    private val oppgaveKøV3Repository: OppgaveKoRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val pepClient: IPepClient,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
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
    }

    suspend fun hentSaksbehandlere(): List<SaksbehandlerDto> {
        return saksbehandlerRepository.hentAlleSaksbehandlere().map { SaksbehandlerDto(it) }.sortedBy { it.navn }
    }

    suspend fun hentAlleAktiveReservasjonerV3(): List<ReservasjonDto> {
        val innloggetBrukerHarKode6Tilgang = pepClient.harTilgangTilKode6()

        return reservasjonV3Tjeneste.hentAlleAktiveReservasjoner().flatMap { reservasjonMedOppgaver ->
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonMedOppgaver.reservasjonV3.reservertAv)!!
            val saksbehandlerHarKode6Tilgang = pepClient.harTilgangTilKode6(saksbehandler.brukerIdent!!)

            if (innloggetBrukerHarKode6Tilgang != saksbehandlerHarKode6Tilgang) {
                emptyList()
            } else {
                reservasjonMedOppgaver.oppgaverV3.map { oppgave ->
                    ReservasjonDto(
                        reservertAvEpost = saksbehandler.epost,
                        reservertAvIdent = saksbehandler.brukerIdent!!,
                        reservertAvNavn = saksbehandler.navn,
                        saksnummer = oppgave.hentVerdi("saksnummer"), //TODO: Oppgaveagnostisk logikk. Løses antagelig ved å skrive om frontend i dette tilfellet
                        journalpostId = oppgave.hentVerdi("journalpostId"),
                        behandlingType = BehandlingType.fraKode(oppgave.hentVerdi("behandlingTypekode")!!),
                        reservertTilTidspunkt = reservasjonMedOppgaver.reservasjonV3.gyldigTil,
                        kommentar = reservasjonMedOppgaver.reservasjonV3.kommentar ?: "",
                        tilBeslutter = oppgave.hentVerdi("liggerHosBeslutter").toBoolean(),
                        oppgavenøkkel = OppgaveNøkkelDto(oppgave),
                    )
                }.toList()

            }
        }
    }
}
