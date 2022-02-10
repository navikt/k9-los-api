package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.*
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class OppgaveIdMedDato(val id: UUID, val dato: LocalDateTime)

data class OppgaveKø(
    val id: UUID,
    var navn: String,
    var sistEndret: LocalDate,
    var sortering: KøSortering,
    var filtreringBehandlingTyper: MutableList<BehandlingType>,
    var filtreringYtelseTyper: MutableList<FagsakYtelseType>,
    var filtreringAndreKriterierType: MutableList<AndreKriterierDto>,
    val enhet: Enhet,
    var fomDato: LocalDate?,
    var tomDato: LocalDate?,
    var saksbehandlere: MutableList<Saksbehandler>,
    var skjermet: Boolean = false,
    var oppgaverOgDatoer: MutableList<OppgaveIdMedDato> = mutableListOf(),
    val kode6: Boolean = false
) {
    private val log: Logger = LoggerFactory.getLogger(OppgaveKø::class.java)

    fun leggOppgaveTilEllerFjernFraKø(
        oppgave: Oppgave,
        reservasjonRepository: ReservasjonRepository? = null
    ): Boolean {
        val tilhørerOppgaveTilKø = tilhørerOppgaveTilKø(
            oppgave = oppgave,
            reservasjonRepository = reservasjonRepository
        )
        if (tilhørerOppgaveTilKø) {
            if (this.oppgaverOgDatoer.none { it.id == oppgave.eksternId }) {
                this.oppgaverOgDatoer.add(
                    OppgaveIdMedDato(
                        oppgave.eksternId,
                        if (sortering == KøSortering.OPPRETT_BEHANDLING) {
                            oppgave.behandlingOpprettet
                        } else {
                            oppgave.forsteStonadsdag.atStartOfDay()
                        }
                    )
                )
                return true
            }
        } else {
            if (this.oppgaverOgDatoer.any { it.id == oppgave.eksternId }) {
                this.oppgaverOgDatoer.remove(this.oppgaverOgDatoer.first { it.id == oppgave.eksternId })
                return true
            }
        }
        return false
    }

    fun tilhørerOppgaveTilKø(
        oppgave: Oppgave,
        reservasjonRepository: ReservasjonRepository?
    ): Boolean {
        if (!oppgave.aktiv) {
            return false
        }

        if (reservasjonRepository != null && erOppgavenReservert(reservasjonRepository, oppgave)) {
            return false
        }
        if (!erInnenforOppgavekøensPeriode(oppgave)) {
            return false
        }

        if (filtreringYtelseTyper.isNotEmpty() && !filtreringYtelseTyper.contains(oppgave.fagsakYtelseType)) {
            return false
        }

        if (filtreringBehandlingTyper.isNotEmpty() && !filtreringBehandlingTyper.contains(oppgave.behandlingType)) {
            return false
        }

        if (oppgave.skjermet != this.skjermet) {
            return false
        }

        if (oppgave.kode6 != this.kode6) {
            return false
        }

        if (filtreringAndreKriterierType.isEmpty()) {
            return true
        }

        if (ekskluderer(oppgave)) {
            return false
        }

        if (filtreringAndreKriterierType.none { it.inkluder }) {
            return true
        }

        if (inkluderer(oppgave)) {
            return true
        }

        return false
    }

    private fun erInnenforOppgavekøensPeriode(oppgave: Oppgave): Boolean {
        if (sortering == KøSortering.OPPRETT_BEHANDLING) {
            if (fomDato != null && oppgave.behandlingOpprettet.toLocalDate().isBefore(fomDato!!.plusDays(1))) {
                return false
            }

            if (tomDato != null && oppgave.behandlingOpprettet.toLocalDate().isAfter(tomDato)) {
                return false
            }
        }

        if (sortering == KøSortering.FORSTE_STONADSDAG) {
            if (fomDato != null && oppgave.forsteStonadsdag.isBefore(fomDato!!.plusDays(1))) {
                return false
            }

            if (tomDato != null && oppgave.forsteStonadsdag.isAfter(tomDato)) {
                return false
            }
        }
        return true
    }

    private fun inkluderer(oppgave: Oppgave): Boolean {
        val inkluderKriterier = filtreringAndreKriterierType.filter { it.inkluder }
        return sjekkOppgavensKriterier(oppgave, inkluderKriterier, true)
    }

    private fun ekskluderer(oppgave: Oppgave): Boolean {
        val ekskluderKriterier = filtreringAndreKriterierType.filter { !it.inkluder }
        return sjekkOppgavensKriterier(oppgave, ekskluderKriterier, false)
    }

    private fun sjekkOppgavensKriterier(oppgave: Oppgave, kriterier: List<AndreKriterierDto>, skalMed: Boolean): Boolean {
        if (oppgave.tilBeslutter && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.TIL_BESLUTTER)) {
            return true
        }

        if (oppgave.avklarMedlemskap && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.AVKLAR_MEDLEMSKAP)) {
            return true
        }

        if (oppgave.system == Fagsystem.PUNSJ.kode && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.FRA_PUNSJ)) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(AVKLAR_KOMPLETT_NOK_FOR_BEREGNING)
            && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.AVKLAR_INNTEKTSMELDING_BEREGNING)
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(AUTO_VENTER_PÅ_KOMPLETT_SØKNAD)
            && kriterier.map {it.andreKriterierType}.contains(AndreKriterierType.VENTER_PÅ_KOMPLETT_SØKNAD)
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(ENDELIG_AVKLAR_KOMPLETT_NOK_FOR_BEREGNING)
            && kriterier.map {it.andreKriterierType}.contains(AndreKriterierType.ENDELIG_BEH_AV_INNTEKTSMELDING)
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(VENT_ANNEN_PSB_SAK)
            && kriterier.map {it.andreKriterierType}.contains(AndreKriterierType.VENTER_PÅ_ANNEN_PARTS_SAK)

        ) {
            return true
        }

        if(oppgave.aksjonspunkter.harEtAvInaktivtAksjonspunkt(
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE,
                OVERSTYR_BEREGNING_INPUT,
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE_ANNEN_PART
            ) && kriterier.map {it.andreKriterierType}.contains(AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD)
              && oppgave.tilBeslutter) {
            return true
        }

        if(oppgave.aksjonspunkter.harAtAvAktivtAksjonspunkt(
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE,
                OVERSTYR_BEREGNING_INPUT,
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE_ANNEN_PART
            ) && kriterier.map {it.andreKriterierType}.contains(AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT)) {
            return true
        }
        return false
    }

    fun erOppgavenReservert(
        reservasjonRepository: ReservasjonRepository,
        oppgave: Oppgave
    ): Boolean {
        if (reservasjonRepository.finnes(oppgave.eksternId)) {
            val reservasjon = reservasjonRepository.hent(oppgave.eksternId)
            return reservasjon.erAktiv()
        }
        return false
    }

}

class Saksbehandler(
    var brukerIdent: String?,
    var navn: String?,
    var epost: String,
    var reservasjoner: MutableSet<UUID> = mutableSetOf(),
    var enhet: String?
)







