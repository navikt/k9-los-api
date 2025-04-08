package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.AVKLAR_FORTSATT_MEDLEMSKAP_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_FOR_SN_NY_I_ARBEIDSLIVET_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_SELVSTENDIG_NÆRINGSDRIVENDE_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.OVERSTYRING_AV_OPPTJENINGSVILKÅRET_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.VURDER_ARBEIDSFORHOLD_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.VURDER_FAKTA_FOR_ATFL_SN_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.VURDER_OPPTJENINGSVILKÅRET_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.VURDER_PERIODER_MED_OPPTJENING_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.VURDER_VARIG_ENDRET_ELLER_NYOPPSTARTET_NÆRING_SELVSTENDIG_NÆRINGSDRIVENDE_KODE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktKodeDefinisjon.VURDER_ÅRSKVANTUM_KVOTE
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.kontrakt.BehandlingAvsluttet
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.kontrakt.BehandlingOpprettet
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.IModell
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.statistikk.kontrakter.Aktør
import no.nav.k9.statistikk.kontrakter.Behandling
import no.nav.k9.statistikk.kontrakter.Sak
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.min

data class K9SakModell(
    val eventer: MutableList<K9SakEventDto>
) : IModell {
    private val `Omsorgspenger, Pleiepenger og opplæringspenger` = "ab0271"

    override fun oppgave(): Oppgave {
        return oppgave(sisteEvent())
    }

    fun oppgave(sisteEvent: K9SakEventDto = sisteEvent()): Oppgave {
        val eventResultat = sisteEvent.tilAktiveAksjonspunkter().eventResultat(Fagsystem.K9SAK)
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
        // feil i dto, sjekker begge feltene
        val behandlingStatus = sisteEvent.behandlingStatus ?: BehandlingStatus.OPPRETTET.kode
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
            aksjonspunkter = sisteEvent.tilAksjonspunkter(),
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
            ansvarligBeslutterForTotrinn = sisteEvent.ansvarligBeslutterForTotrinn,
            nyeKrav = sisteEvent.nyeKrav ?: false,
            fraEndringsdialog = sisteEvent.fraEndringsdialog ?: false
        )
    }

    private fun avklarMedlemskap(event: K9SakEventDto): Boolean {
        return event.tilAksjonspunkter().hentAktive().any { entry ->
            (entry.key == AVKLAR_FORTSATT_MEDLEMSKAP_KODE)
        }
    }

    private fun avklarArbeidsforhold(event: K9SakEventDto): Boolean {
        return event.tilAksjonspunkter().hentAktive().any { entry ->
            (entry.key == VURDER_ARBEIDSFORHOLD_KODE)
        }
    }

    private fun vurderopptjeningsvilkåret(event: K9SakEventDto): Boolean {
        return event.tilAksjonspunkter().hentAktive().any { entry ->
            (entry.key == VURDER_OPPTJENINGSVILKÅRET_KODE || entry.key == VURDER_PERIODER_MED_OPPTJENING_KODE || entry.key == OVERSTYRING_AV_OPPTJENINGSVILKÅRET_KODE)
        }
    }

    private fun erÅrskvantum(event: K9SakEventDto): Boolean {
        return event.tilAksjonspunkter().hentAktive().any { entry ->
            (entry.key == VURDER_ÅRSKVANTUM_KVOTE)
        }
    }

    private fun erUtenlands(event: K9SakEventDto): Boolean {
        return event.tilAksjonspunkter().hentAktive().any { entry ->
            (entry.key == AUTOMATISK_MARKERING_AV_UTENLANDSSAK_KODE
                    || entry.key == MANUELL_MARKERING_AV_UTLAND_SAKSTYPE_KODE) && entry.value != AksjonspunktStatus.AVBRUTT.kode
        }
    }

    private fun erSelvstendigNæringsdrivndeEllerFrilanser(event: K9SakEventDto): Boolean {
        return event.tilAksjonspunkter().hentAktive().any { entry ->
            (
                    entry.key == FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS_KODE ||
                            entry.key == VURDER_VARIG_ENDRET_ELLER_NYOPPSTARTET_NÆRING_SELVSTENDIG_NÆRINGSDRIVENDE_KODE ||
                            entry.key == FASTSETT_BEREGNINGSGRUNNLAG_SELVSTENDIG_NÆRINGSDRIVENDE_KODE ||
                            entry.key == FASTSETT_BEREGNINGSGRUNNLAG_FOR_SN_NY_I_ARBEIDSLIVET_KODE ||
                            entry.key == VURDER_FAKTA_FOR_ATFL_SN_KODE)
        }
    }

    fun sisteEvent(): K9SakEventDto {
        return this.eventer[this.eventer.lastIndex]
    }

    fun forrigeEvent(): K9SakEventDto? {
        return if (this.eventer.lastIndex > 0) {
            this.eventer[this.eventer.lastIndex - 1]
        } else {
            null
        }
    }

    fun førsteEvent(): K9SakEventDto {
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
        val reservasjon = reservasjonRepository.hentOptional(oppgave.eksternId)
        val beslutter = if (oppgave.tilBeslutter && reservasjon != null) {
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdentInkluderKode6(reservasjon.reservertAv)
            saksbehandler?.brukerIdent
        } else {
            ""
        }

        val behandldendeEnhet =
            if (reservasjon != null) {
                val hentMedHistorikk = reservasjonRepository.hentMedHistorikk(oppgave.eksternId)
                val reservertav = hentMedHistorikk.map { it.reservertAv }.first()
                saksbehandlerRepository.finnSaksbehandlerMedIdentInkluderKode6(reservertav)?.enhet?.substringBefore(" ")
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

        val blittBeslutter =
            !forrigeEvent.tilAksjonspunkter().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK) &&
                sisteEvent().tilAksjonspunkter().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK)

        if (blittBeslutter) return true

        // har blitt satt på vent
        if (sisteEvent().ytelseTypeKode == FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode
            && sisteEvent().tilAksjonspunkter().påVent(Fagsystem.K9SAK))
            return true

        val beslutterFerdig =
            forrigeEvent.tilAksjonspunkter().harAktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK) &&
                sisteEvent().tilAksjonspunkter().harInaktivtAksjonspunkt(AksjonspunktDefinisjon.FATTER_VEDTAK)

        if (beslutterFerdig) return true

        return false
    }

    // Array med alle versjoner av modell basert på eventene, brukes når man skal spille av eventer
    fun alleVersjoner(): MutableList<K9SakModell> {
        val eventListe = mutableListOf<K9SakEventDto>()
        val modeller = mutableListOf<K9SakModell>()
        for (behandlingProsessEventDto in eventer) {
            eventListe.add(behandlingProsessEventDto)
            modeller.add(K9SakModell(eventListe.toMutableList()))
        }
        return modeller
    }

}


