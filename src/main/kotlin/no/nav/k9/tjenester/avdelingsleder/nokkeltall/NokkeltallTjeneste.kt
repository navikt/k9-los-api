package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.StatistikkRepository

class NokkeltallTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkRepository: StatistikkRepository
) {

    suspend fun hentOppgaverUnderArbeid(): List<AlleOppgaverDto> {
        return oppgaveRepository.hentAlleOppgaverUnderArbeid()
    }

    fun hentNyeFerdigstilteOppgaverOppsummering(): List<AlleOppgaverNyeOgFerdigstilteDto> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(7).map {
            AlleOppgaverNyeOgFerdigstilteDto(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.nye.size,
                    it.ferdigstilteSaksbehandler.size,
                    )
        }
    }

    fun hentFerdigstilteSiste8Uker(): List<AlleOppgaverHistorikk> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkSiste8Uker().map {
            AlleOppgaverHistorikk(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.ferdigstilteSaksbehandler.size
            )
        }
    }

    fun hentNyeSiste8Uker(): List<AlleOppgaverHistorikk> {
        return statistikkRepository.hentFerdigstilteOgNyeHistorikkSiste8Uker().map {
            AlleOppgaverHistorikk(
                    it.fagsakYtelseType,
                    it.behandlingType,
                    it.dato,
                    it.nye.size
            )
        }
    }

    suspend fun hentDagensTall(): List<AlleApneBehandlinger> {
        return oppgaveRepository.hentApneBehandlingerPerBehandlingtypeIdag()
    }
}
