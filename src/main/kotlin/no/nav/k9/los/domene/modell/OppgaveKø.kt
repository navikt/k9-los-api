package no.nav.k9.los.domene.modell

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon.*
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import no.nav.k9.los.tjenester.saksbehandler.merknad.Merknad
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
    var filtreringBehandlingTyper: MutableList<BehandlingType> = mutableListOf(),
    var filtreringYtelseTyper: MutableList<FagsakYtelseType> = mutableListOf(),
    var filtreringAndreKriterierType: MutableList<AndreKriterierDto> = mutableListOf(),
    val enhet: Enhet = Enhet.NASJONAL,
    var fomDato: LocalDate? = null,
    var tomDato: LocalDate? = null,
    var saksbehandlere: MutableList<Saksbehandler>,
    var skjermet: Boolean = false,
    var oppgaverOgDatoer: MutableList<OppgaveIdMedDato> = mutableListOf(),
    val kode6: Boolean = false,
    var filtreringFeilutbetaling: Intervall<Long>? = null,
    var merknadKoder: List<String> = emptyList(),
    var oppgaveKoder: List<String> = emptyList(),
    var nyeKrav: Boolean? = null,
    var fraEndringsdialog: Boolean? = null
) {

    companion object {
        private val log = LoggerFactory.getLogger(OppgaveKø::class.java)
        fun erOppgavenReservert(
            reservasjonRepository: ReservasjonRepository,
            oppgave: Oppgave
        ): Boolean {
            val reservasjon = reservasjonRepository.hentOptional(oppgave.eksternId)
            if (reservasjon != null) {
                return reservasjon.erAktiv()
            }
            return false
        }
    }

    fun leggOppgaveTilEllerFjernFraKø(
        oppgave: Oppgave,
        reservasjonRepository: ReservasjonRepository,
        merknader: List<Merknad>
    ): Boolean {
        return leggOppgaveTilEllerFjernFraKø(oppgave, { erOppgavenReservert(reservasjonRepository, it) }, merknader)
    }

    fun leggOppgaveTilEllerFjernFraKø(
        oppgave: Oppgave,
        erOppgavenReservertSjekk : (Oppgave) -> Boolean,
        merknader: List<Merknad>
    ): Boolean {
        val tilhørerOppgaveTilKø = tilhørerOppgaveTilKø(
            oppgave = oppgave,
            erOppgavenReservertSjekk = erOppgavenReservertSjekk,
            merknader
        )
        if (tilhørerOppgaveTilKø) {
            if (this.oppgaverOgDatoer.none { it.id == oppgave.eksternId }) {
                log.info("Legger til oppgave ${oppgave.eksternId} i kø $navn")
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
                log.info("Fjerner oppgave ${oppgave.eksternId} fra kø $navn")
                this.oppgaverOgDatoer.remove(this.oppgaverOgDatoer.first { it.id == oppgave.eksternId })
                return true
            }
        }
        return false
    }

    fun tilhørerOppgaveTilKø(
        oppgave: Oppgave,
        reservasjonRepository: ReservasjonRepository,
        merknader: List<Merknad>
    ): Boolean {
        return tilhørerOppgaveTilKø(oppgave, { erOppgavenReservert(reservasjonRepository, it) } , merknader )
    }

    fun tilhørerOppgaveTilKø(
        oppgave: Oppgave,
        erOppgavenReservertSjekk : (Oppgave) -> Boolean,
        merknader: List<Merknad>
    ): Boolean {
        if (!oppgave.aktiv) {
            return false
        }

        if (erOppgavenReservertSjekk.invoke(oppgave)) {
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

        if (merknadKoder.isEmpty() && merknader.isNotEmpty() || !merknader.flatMap { it.merknadKoder }
                .containsAll(merknadKoder)) {
            return false
        }

        if (oppgave.feilutbetaltBeløp != null && filtreringFeilutbetaling != null
            && filtreringFeilutbetaling!!.erUtenfor(oppgave.feilutbetaltBeløp)
        ) {
            return false
        }

        if (nyeKrav != null && nyeKrav != oppgave.nyeKrav) {
            return false
        }

        if (fraEndringsdialog != null && fraEndringsdialog != oppgave.fraEndringsdialog) {
            return false
        }

        if (oppgaveKoder.isNotEmpty() && oppgave.aksjonspunkter.hentAktive().isEmpty() ||
            oppgaveKoder.isNotEmpty() && oppgave.aksjonspunkter.hentAktive().isNotEmpty() &&
            !oppgaveKoder.containsAll(oppgave.aksjonspunkter.hentAktive().keys)
        ) {
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
            if (fomDato != null && oppgave.behandlingOpprettet.toLocalDate().isBefore(fomDato!!)) {
                return false
            }

            if (tomDato != null && oppgave.behandlingOpprettet.toLocalDate().isAfter(tomDato)) {
                return false
            }
        }

        if (sortering == KøSortering.FORSTE_STONADSDAG) {
            if (fomDato != null && oppgave.forsteStonadsdag.isBefore(fomDato!!)) {
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

    private fun sjekkOppgavensKriterier(
        oppgave: Oppgave,
        kriterier: List<AndreKriterierDto>,
        skalMed: Boolean
    ): Boolean {
        if (oppgave.tilBeslutter && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.TIL_BESLUTTER)) {
            return true
        }

        if (oppgave.avklarMedlemskap && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.AVKLAR_MEDLEMSKAP)) {
            return true
        }

        if (oppgave.årskvantum && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.AARSKVANTUM)) {
            return true
        }

        if (oppgave.system == Fagsystem.PUNSJ.kode && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.FRA_PUNSJ)) {
            return true
        }

        if (oppgave.journalførtTidspunkt == null && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.IKKE_JOURNALFØRT)) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(AVKLAR_KOMPLETT_NOK_FOR_BEREGNING)
            && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.AVKLAR_INNTEKTSMELDING_BEREGNING)
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(AUTO_VENTER_PÅ_KOMPLETT_SØKNAD)
            && kriterier.map { it.andreKriterierType }.contains(AndreKriterierType.VENTER_PÅ_KOMPLETT_SØKNAD)
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(ENDELIG_AVKLAR_KOMPLETT_NOK_FOR_BEREGNING)
            && kriterier.map { it.andreKriterierType }.contains(AndreKriterierType.ENDELIG_BEH_AV_INNTEKTSMELDING)
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harAktivtAksjonspunkt(VENT_ANNEN_PSB_SAK)
            && kriterier.map { it.andreKriterierType }.contains(AndreKriterierType.VENTER_PÅ_ANNEN_PARTS_SAK)

        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harEtAvInaktivtAksjonspunkt(
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE,
                OVERSTYR_BEREGNING_INPUT,
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE_ANNEN_PART
            ) && kriterier.map { it.andreKriterierType }.contains(AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD)
            && oppgave.tilBeslutter
        ) {
            return true
        }

        if (oppgave.aksjonspunkter.harEtAvAksjonspunkt(
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE,
                OVERSTYR_BEREGNING_INPUT,
                TRENGER_SØKNAD_FOR_INFOTRYGD_PERIODE_ANNEN_PART
            ) && kriterier.map { it.andreKriterierType }
                .contains(AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT)
            && !oppgave.tilBeslutter
        ) {
            return true
        }
        return false
    }

    fun beslutterKø() = filtreringAndreKriterierType
        .filter { it.inkluder }
        .map { it.andreKriterierType }
        .contains(AndreKriterierType.TIL_BESLUTTER)



    fun lagKriterier(): List<KriteriumDto> {
        val kriterierDto = mutableListOf<KriteriumDto>()
        if (filtreringFeilutbetaling != null) {
            kriterierDto.add(tilFeilutbetalingKriterium())
        }

        if (merknadKoder.isNotEmpty()) {
            kriterierDto.add(tilMerknadKriterium())
        }

        if (oppgaveKoder.isNotEmpty()) {
            kriterierDto.add(tilOppgaveKodeKriterium())
        }

        if (nyeKrav != null) {
            kriterierDto.add(tilNyeKravKriterium())
        }

        if (fraEndringsdialog != null) {
            kriterierDto.add(fraEndringsdialogKriterium())
        }

        return kriterierDto
    }

    private fun tilFeilutbetalingKriterium() = KriteriumDto(
        id = id.toString(),
        kriterierType = KøKriterierType.FEILUTBETALING,
        checked = true,
        fom = filtreringFeilutbetaling!!.fom?.toString(),
        tom = filtreringFeilutbetaling!!.tom?.toString()
    )

    private fun tilMerknadKriterium() = KriteriumDto(
        id = id.toString(),
        kriterierType = KøKriterierType.MERKNADTYPE,
        checked = true,
        koder = merknadKoder
    )

    private fun tilOppgaveKodeKriterium() = KriteriumDto(
        id = id.toString(),
        kriterierType = KøKriterierType.OPPGAVEKODE,
        checked = true,
        koder = oppgaveKoder
    )

    private fun tilNyeKravKriterium() = KriteriumDto(
        id = id.toString(),
        kriterierType = KøKriterierType.NYE_KRAV,
        checked = true,
        inkluder = nyeKrav
    )

    private fun fraEndringsdialogKriterium() = KriteriumDto(
        id = id.toString(),
        kriterierType = KøKriterierType.FRA_ENDRINGSDIALOG,
        checked = true,
        inkluder = fraEndringsdialog
    )
}







