package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.*
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.statistikk.kontrakter.Aktør
import no.nav.k9.statistikk.kontrakter.Behandling
import no.nav.k9.statistikk.kontrakter.Sak
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.min

data class K9SakModell(
    val eventer: MutableList<BehandlingProsessEventDto>
) : IModell {
    private val `Omsorgspenger, Pleiepenger og opplæringspenger` = "ab0271"

    override fun oppgave(): Oppgave {
        return oppgave(sisteEvent())
    }

    fun oppgave(sisteEvent: BehandlingProsessEventDto = sisteEvent()): Oppgave {
        val eventResultat = sisteEvent.aktiveAksjonspunkt().eventResultat()
        var aktiv = true
        var oppgaveAvsluttet: LocalDateTime? = null

        if (eventResultat.lukkerOppgave()) {
            aktiv = false
            oppgaveAvsluttet = sisteEvent.eventTid
        }

        if (sisteEvent.eventHendelse == EventHendelse.AKSJONSPUNKT_AVBRUTT || sisteEvent.eventHendelse == EventHendelse.AKSJONSPUNKT_UTFØRT) {
            aktiv = false
        }
        if (FagsakYtelseType.fraKode(sisteEvent.ytelseTypeKode) == FagsakYtelseType.FRISINN) {
            aktiv = false
        }
        var behandlingStatus = sisteEvent.behandlingStatus
        // feil i dto, sjekker begge feltene
        behandlingStatus = behandlingStatus ?: sisteEvent.behandlinStatus ?: BehandlingStatus.OPPRETTET.kode
        if (behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
            aktiv = false
        }
        return Oppgave(
            behandlingId = sisteEvent.behandlingId,
            fagsakSaksnummer = sisteEvent.saksnummer,
            aktorId = sisteEvent.aktørId,
            journalpostId = null,
            behandlendeEnhet = sisteEvent.behandlendeEnhet ?: "",
            behandlingType = BehandlingType.fraKode(sisteEvent.behandlingTypeKode),
            fagsakYtelseType = FagsakYtelseType.fraKode(sisteEvent.ytelseTypeKode),
            aktiv = aktiv,
            forsteStonadsdag = sisteEvent.eventTid.toLocalDate(),
            utfortFraAdmin = false,
            behandlingsfrist = sisteEvent.behandlingstidFrist?.atStartOfDay() ?: sisteEvent.eventTid.plusDays(21),
            behandlingStatus = BehandlingStatus.fraKode(behandlingStatus),
            eksternId = sisteEvent.eksternId ?: UUID.randomUUID(),
            behandlingOpprettet = sisteEvent.opprettetBehandling,
            oppgaveAvsluttet = oppgaveAvsluttet,
            system = sisteEvent.fagsystem.name,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = sisteEvent.aktiveAksjonspunkt(),
            utenlands = erUtenlands(sisteEvent),
            tilBeslutter = eventResultat.beslutterOppgave(),
            selvstendigFrilans = erSelvstendigNæringsdrivndeEllerFrilanser(sisteEvent),
            søktGradering = false,
            utbetalingTilBruker = false,
            kode6 = false,
            årskvantum = erÅrskvantum(sisteEvent),
            avklarMedlemskap = avklarMedlemskap(sisteEvent),
            avklarArbeidsforhold = avklarArbeidsforhold(sisteEvent),
            vurderopptjeningsvilkåret = vurderopptjeningsvilkåret(sisteEvent),
            eventTid = sisteEvent.eventTid,
            ansvarligSaksbehandlerForTotrinn = sisteEvent.ansvarligSaksbehandlerForTotrinn,
            ansvarligSaksbehandlerIdent = sisteEvent.ansvarligSaksbehandlerIdent,
            fagsakPeriode = if (sisteEvent.fagsakPeriode != null) Oppgave.FagsakPeriode(
                sisteEvent.fagsakPeriode.fom,
                sisteEvent.fagsakPeriode.tom
            ) else null,
            pleietrengendeAktørId = sisteEvent.pleietrengendeAktørId,
            relatertPartAktørId = sisteEvent.relatertPartAktørId,
            kombinert = false,
            ansvarligBeslutterForTotrinn = sisteEvent.ansvarligBeslutterForTotrinn
        )
    }

    private fun avklarMedlemskap(event: BehandlingProsessEventDto): Boolean {
        return event.aktiveAksjonspunkt().liste.any { entry ->
            (entry.key == AVKLAR_FORTSATT_MEDLEMSKAP_KODE)
        }
    }

    private fun avklarArbeidsforhold(event: BehandlingProsessEventDto): Boolean {
        return event.aktiveAksjonspunkt().liste.any { entry ->
            (entry.key == VURDER_ARBEIDSFORHOLD_KODE)
        }
    }

    private fun vurderopptjeningsvilkåret(event: BehandlingProsessEventDto): Boolean {
        return event.aktiveAksjonspunkt().liste.any { entry ->
            (entry.key == VURDER_OPPTJENINGSVILKÅRET_KODE || entry.key == VURDER_PERIODER_MED_OPPTJENING_KODE || entry.key == OVERSTYRING_AV_OPPTJENINGSVILKÅRET_KODE)
        }
    }

    private fun erÅrskvantum(event: BehandlingProsessEventDto): Boolean {
        return event.aktiveAksjonspunkt().liste.any { entry ->
            (entry.key == VURDER_ÅRSKVANTUM_KVOTE)
        }
    }

    private fun erUtenlands(event: BehandlingProsessEventDto): Boolean {
        return event.aktiveAksjonspunkt().liste.any { entry ->
            (entry.key == AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE
                    || entry.key == MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE) && entry.value != AksjonspunktStatus.AVBRUTT.kode
        }
    }

    private fun erSelvstendigNæringsdrivndeEllerFrilanser(event: BehandlingProsessEventDto): Boolean {
        return event.aktiveAksjonspunkt().liste.any { entry ->
            (
                    entry.key == FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS_KODE ||
                            entry.key == VURDER_VARIG_ENDRET_ELLER_NYOPPSTARTET_NÆRING_SELVSTENDIG_NÆRINGSDRIVENDE_KODE ||
                            entry.key == FASTSETT_BEREGNINGSGRUNNLAG_SELVSTENDIG_NÆRINGSDRIVENDE_KODE ||
                            entry.key == FASTSETT_BEREGNINGSGRUNNLAG_FOR_SN_NY_I_ARBEIDSLIVET_KODE ||
                            entry.key == VURDER_FAKTA_FOR_ATFL_SN_KODE)
        }
    }

    fun sisteEvent(): BehandlingProsessEventDto {
        return this.eventer[this.eventer.lastIndex]
    }

    fun forrigeEvent(): BehandlingProsessEventDto? {
        return if (this.eventer.lastIndex > 0) {
            this.eventer[this.eventer.lastIndex - 1]
        } else {
            null
        }
    }

    fun førsteEvent(): BehandlingProsessEventDto {
        return this.eventer[0]
    }

    override fun starterSak(): Boolean {
        return this.eventer.size == 1
    }

    override fun erTom(): Boolean {
        return this.eventer.isEmpty()
    }

    override fun dvhSak(): Sak {
        val oppgave = oppgave()
        val zone = ZoneId.of("Europe/Oslo")
        return Sak(
            saksnummer = oppgave.fagsakSaksnummer,
            sakId = oppgave.fagsakSaksnummer,
            funksjonellTid = sisteEvent().eventTid.atOffset(zone.rules.getOffset(sisteEvent().eventTid)),
            tekniskTid = OffsetDateTime.now(),
            opprettetDato = oppgave.behandlingOpprettet.toLocalDate(),
            aktorId = oppgave.aktorId.toLong(),
            aktorer = listOf(Aktør(oppgave.aktorId.toLong(), "Søker", "Søker")),
            ytelseType = oppgave.fagsakYtelseType.navn,
            underType = null,
            sakStatus = oppgave.behandlingStatus.navn,
            ytelseTypeBeskrivelse = null,
            underTypeBeskrivelse = null,
            sakStatusBeskrivelse = null,
            avsender = "K9los",
            versjon = 1
        )
    }

    override fun behandlingOpprettetSakOgBehandling(

    ): BehandlingOpprettet {
        val sisteEvent = sisteEvent()
        return BehandlingOpprettet(
            hendelseType = "behandlingOpprettet",
            hendelsesId = sisteEvent.eksternId.toString() + "_" + eventer.size,
            hendelsesprodusentREF = BehandlingOpprettet.HendelsesprodusentREF("", "", "FS39"),
            hendelsesTidspunkt = sisteEvent.eventTid,
            behandlingsID = ("k9-los-" + sisteEvent.eksternId).substring(
                0,
                min(31, ("k9-los-" + sisteEvent.eksternId).length - 1)
            ),
            behandlingstype = BehandlingOpprettet.Behandlingstype(
                "",
                "",
                BehandlingType.fraKode(sisteEvent.behandlingTypeKode).kodeverk
            ),
            sakstema = BehandlingOpprettet.Sakstema("", "", "OMS"),
            behandlingstema = BehandlingOpprettet.Behandlingstema(
                "ab0149",
                "ab0149",
                `Omsorgspenger, Pleiepenger og opplæringspenger`
            ),
            aktoerREF = listOf(BehandlingOpprettet.AktoerREF(sisteEvent.aktørId)),
            ansvarligEnhetREF = "NASJONAL",
            primaerBehandlingREF = null,
            sekundaerBehandlingREF = listOf(),
            applikasjonSakREF = sisteEvent().saksnummer,
            applikasjonBehandlingREF = sisteEvent().eksternId.toString().replace("-", ""),
            styringsinformasjonListe = listOf()
        )
    }

    override fun behandlingAvsluttetSakOgBehandling(
    ): BehandlingAvsluttet {
        val sisteEvent = sisteEvent()
        return BehandlingAvsluttet(
            hendelseType = "behandlingAvsluttet",
            hendelsesId = """${sisteEvent.eksternId.toString()}_${eventer.size}""",
            hendelsesprodusentREF = BehandlingAvsluttet.HendelsesprodusentREF("", "", "FS39"),
            hendelsesTidspunkt = sisteEvent.eventTid,
            behandlingsID = ("k9-los-" + sisteEvent.eksternId).substring(
                0,
                min(31, ("k9-los-" + sisteEvent.eksternId).length - 1)
            ),
            behandlingstype = BehandlingAvsluttet.Behandlingstype(
                "",
                "",
                BehandlingType.fraKode(sisteEvent.behandlingTypeKode).kodeverk
            ),
            sakstema = BehandlingAvsluttet.Sakstema("", "", "OMS"),
            behandlingstema = BehandlingAvsluttet.Behandlingstema(
                "ab0149",
                "ab0149",
                `Omsorgspenger, Pleiepenger og opplæringspenger`
            ),
            aktoerREF = listOf(BehandlingAvsluttet.AktoerREF(sisteEvent.aktørId)),
            ansvarligEnhetREF = "NASJONAL",
            primaerBehandlingREF = null,
            sekundaerBehandlingREF = listOf(),
            applikasjonSakREF = sisteEvent().saksnummer,
            applikasjonBehandlingREF = sisteEvent().eksternId.toString().replace("-", ""),
            styringsinformasjonListe = listOf(),
            avslutningsstatus = BehandlingAvsluttet.Avslutningsstatus("", "", "ok")
        )
    }

    override fun sisteSaksNummer(): String {
        return sisteEvent().saksnummer
    }

    override fun dvhBehandling(
        saksbehandlerRepository: SaksbehandlerRepository,
        reservasjonRepository: ReservasjonRepository
    ): Behandling {
        val oppgave = oppgave()
        val beslutter = if (oppgave.tilBeslutter
            && reservasjonRepository.finnes(oppgave.eksternId) && reservasjonRepository.finnes(oppgave.eksternId)
        ) {
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedIdentIkkeTaHensyn(reservasjonRepository.hent(oppgave.eksternId).reservertAv)
            saksbehandler?.brukerIdent
        } else {
            ""
        }

        val behandldendeEnhet =
            if (reservasjonRepository.finnes(oppgave.eksternId)) {
                val hentMedHistorikk = reservasjonRepository.hentMedHistorikk(oppgave.eksternId)
                val reservertav = hentMedHistorikk
                    .map { reservasjon -> reservasjon.reservertAv }.first()
                saksbehandlerRepository.finnSaksbehandlerMedIdentIkkeTaHensyn(reservertav)?.enhet?.substringBefore(" ")
            } else {
                "SRV"
            }
        val zone = ZoneId.of("Europe/Oslo")
        return Behandling(
            sakId = oppgave.fagsakSaksnummer,
            behandlingId = oppgave.eksternId.toString(),
            funksjonellTid = sisteEvent().eventTid.atOffset(zone.rules.getOffset(sisteEvent().eventTid)),
            tekniskTid = OffsetDateTime.now(),
            mottattDato = oppgave.behandlingOpprettet.toLocalDate(),
            registrertDato = oppgave.behandlingOpprettet.toLocalDate(),
            vedtaksDato = null,
            relatertBehandlingId = null,
            vedtakId = null,
            saksnummer = oppgave.fagsakSaksnummer,
            behandlingType = oppgave.behandlingType.navn,
            behandlingStatus = oppgave.behandlingStatus.navn,
            resultat = sisteEvent().resultatType,
            resultatBegrunnelse = null,
            utenlandstilsnitt = oppgave.utenlands.toString(),
            behandlingTypeBeskrivelse = null,
            behandlingStatusBeskrivelse = null,
            resultatBeskrivelse = null,
            resultatBegrunnelseBeskrivelse = null,
            utenlandstilsnittBeskrivelse = null,
            beslutter = beslutter,
            saksbehandler = null,
            behandlingOpprettetAv = "system",
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            ansvarligEnhetKode = behandldendeEnhet,
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = behandldendeEnhet,
            behandlendeEnhetType = "NORG",
            datoForUttak = null,
            datoForUtbetaling = null,
            totrinnsbehandling = oppgave.tilBeslutter,
            avsender = "K9sak",
            versjon = 1
        )
    }

    override fun fikkEndretAksjonspunkt(): Boolean {
        val forrigeEvent = forrigeEvent() ?: return false

        if (sisteEvent().ytelseTypeKode == "PSB") {
            // har blitt beslutter
            if (!forrigeEvent.aktiveAksjonspunkt().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK) &&
                sisteEvent().aktiveAksjonspunkt().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK)) {
                return true
            }
            // har blitt satt på vent
            if (sisteEvent().aktiveAksjonspunkt().påVent()) {
                return true
            }

            // beslutter har gjort seg ferdig
            if (forrigeEvent.aktiveAksjonspunkt().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK) &&
                sisteEvent().alleAksjonspunkter().harInaktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK)
            ) {
                return true
            }
            // skal fortsette og ligge reservert
            return false
        } else {
            // har blitt beslutter
            if (!forrigeEvent.aktiveAksjonspunkt().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK) &&
                sisteEvent().aktiveAksjonspunkt().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK)) {
                return true
            }

            // beslutter har gjort seg ferdig
            if (forrigeEvent.aktiveAksjonspunkt().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK) &&
                sisteEvent().alleAksjonspunkter().harInaktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK)
            ) {
                return true
            }

            val forrigeAksjonspunkter = forrigeEvent.aktiveAksjonspunkt().liste
            val nåværendeAksjonspunkter = sisteEvent().aktiveAksjonspunkt().liste

            if (sisteEvent().aktiveAksjonspunkt().lengde() > 0 && !sisteEvent().aktiveAksjonspunkt().tilBeslutter()) {
                return false
            }
            return forrigeAksjonspunkter != nåværendeAksjonspunkter
        }
    }

    // Array med alle versjoner av modell basert på eventene, brukes når man skal spille av eventer
    fun alleVersjoner(): MutableList<K9SakModell> {
        val eventListe = mutableListOf<BehandlingProsessEventDto>()
        val modeller = mutableListOf<K9SakModell>()
        for (behandlingProsessEventDto in eventer) {
            eventListe.add(behandlingProsessEventDto)
            modeller.add(K9SakModell(eventListe.toMutableList()))
        }
        return modeller
    }

}

fun BehandlingProsessEventDto.aktiveAksjonspunkt(): Aksjonspunkter {
    return Aksjonspunkter(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == AksjonspunktStatus.OPPRETTET.kode })
}

fun BehandlingProsessEventDto.alleAksjonspunkter(): Aksjonspunkter {
    return Aksjonspunkter(this.aksjonspunktKoderMedStatusListe)
}

data class Aksjonspunkter(val liste: Map<String, String>) {
    fun lengde(): Int {
        return liste.size
    }

    fun påVent(): Boolean {
        return AksjonspunktDefWrapper.påVent(this.liste)
    }

    fun erTom(): Boolean {
        return this.liste.isEmpty()
    }

    fun tilBeslutter(): Boolean {
        return AksjonspunktDefWrapper.tilBeslutter(this.liste)
    }

    fun harAktivtAksjonspunkt(def: AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtAktivtAksjonspunktMedKoden(this.liste, def)
    }

    fun alleAktiveAksjonspunktTaBortPunsj(): Aksjonspunkter {
        return Aksjonspunkter(
            liste.filter { entry -> entry.value == AksjonspunktStatus.OPPRETTET.kode }
                .filter { entry -> !AksjonspunktDefWrapper.aksjonspunkterFraPunsj().map { it.kode }.contains(entry.key)  }
        )
    }

    fun harInaktivtAksjonspunkt(def: AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtInaktivtAksjonspunktMedKoden(this.liste, def)
    }

    fun harEtAvInaktivtAksjonspunkt(vararg def : AksjonspunktDefinisjon): Boolean {
        return AksjonspunktDefWrapper.inneholderEtAvInaktivtAksjonspunkterMedKoder(this.liste, def.toList())
    }

    fun eventResultat(): EventResultat {
        if (erTom()) {
            return EventResultat.LUKK_OPPGAVE
        }

        if (påVent()) {
            return EventResultat.LUKK_OPPGAVE_VENT
        }

        if (tilBeslutter()) {
            return EventResultat.OPPRETT_BESLUTTER_OPPGAVE
        }

        return EventResultat.OPPRETT_OPPGAVE
    }
}


