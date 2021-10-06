package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import java.time.LocalDate

class NokkeltallTjeneste constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkRepository: StatistikkRepository
) {

    suspend fun hentOppgaverUnderArbeid(): List<AlleOppgaverDto> {
        return oppgaveRepository.hentAlleOppgaverUnderArbeid()
    }

    fun hentOppgaverPåVent(): List<AlleOppgaverHistorikk> {
        val oppgaverPåVent = oppgaveRepository.hentAllePåVent()
        val oppgaverPerYtelseBehandlingDato = oppgaverPåVent.groupBy {
            YtelseBehandlingDato(
                it.fagsakYtelseType,
                it.behandlingType,
                Fagsystem.fraKode(it.system),
                it.behandlingsfrist.toLocalDate()
            )
        }
        return oppgaverPerYtelseBehandlingDato.map { (key, value) ->
            AlleOppgaverHistorikk(key.fagsakYtelseType, key.behandlingType, key.dato, key.fagsystem, value.size)
        }
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
                it.kilde,
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
                it.kilde,
                it.nye.size
            )
        }
    }

    suspend fun hentDagensTall(): List<AlleApneBehandlinger> {
        return oppgaveRepository.hentApneBehandlingerPerBehandlingtypeIdag()
    }
}


private data class YtelseBehandlingDato(
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val fagsystem: Fagsystem,
    val dato: LocalDate,
)
