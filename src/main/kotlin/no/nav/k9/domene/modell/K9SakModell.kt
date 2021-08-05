package no.nav.k9.domene.modell

import io.ktor.util.*
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
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
        val event = sisteEvent
        val eventResultat = sisteEvent.aktiveAksjonspunkt().eventResultat()

        var aktiv = true
        var oppgaveAvsluttet: LocalDateTime? = null
        var beslutterOppgave = false

        when (eventResultat) {
            EventResultat.LUKK_OPPGAVE -> {
                aktiv = false
                oppgaveAvsluttet = event.eventTid
            }
            EventResultat.LUKK_OPPGAVE_VENT -> {
                aktiv = false
                oppgaveAvsluttet = event.eventTid
            }
            EventResultat.LUKK_OPPGAVE_MANUELT_VENT -> {
                aktiv = false
                oppgaveAvsluttet = event.eventTid
            }
            EventResultat.GJENÅPNE_OPPGAVE -> TODO()
            EventResultat.OPPRETT_BESLUTTER_OPPGAVE -> {
                beslutterOppgave = true
            }
            EventResultat.OPPRETT_OPPGAVE -> {
                aktiv = true
            }
        }
        if (event.eventHendelse == EventHendelse.AKSJONSPUNKT_AVBRUTT || event.eventHendelse == EventHendelse.AKSJONSPUNKT_UTFØRT) {
            aktiv = false
        }
        if (FagsakYtelseType.fraKode(event.ytelseTypeKode) == FagsakYtelseType.FRISINN) {
            aktiv = false
        }
        var behandlingStatus = event.behandlingStatus
        // feil i dto, sjekker begge feltene
        behandlingStatus = behandlingStatus ?: event.behandlinStatus ?: BehandlingStatus.OPPRETTET.kode
        if (behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
            aktiv = false
        }
        return Oppgave(
            behandlingId = event.behandlingId,
            fagsakSaksnummer = event.saksnummer,
            aktorId = event.aktørId,
            journalpostId = null,
            behandlendeEnhet = event.behandlendeEnhet ?: "",
            behandlingType = BehandlingType.fraKode(event.behandlingTypeKode),
            fagsakYtelseType = FagsakYtelseType.fraKode(event.ytelseTypeKode),
            aktiv = aktiv,
            forsteStonadsdag = event.eventTid.toLocalDate(),
            utfortFraAdmin = false,
            behandlingsfrist = event.eventTid.plusDays(21),
            behandlingStatus = BehandlingStatus.fraKode(behandlingStatus),
            eksternId = event.eksternId ?: UUID.randomUUID(),
            behandlingOpprettet = event.opprettetBehandling,
            oppgaveAvsluttet = oppgaveAvsluttet,
            system = event.fagsystem.name,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = event.aktiveAksjonspunkt(),
            utenlands = erUtenlands(event),
            tilBeslutter = beslutterOppgave,
            selvstendigFrilans = erSelvstendigNæringsdrivndeEllerFrilanser(event),
            søktGradering = false,
            utbetalingTilBruker = false,
            kode6 = false,
            årskvantum = erÅrskvantum(event),
            avklarMedlemskap = avklarMedlemskap(event),
            avklarArbeidsforhold = avklarArbeidsforhold(event),
            vurderopptjeningsvilkåret = vurderopptjeningsvilkåret(event),
            eventTid = event.eventTid,
            ansvarligSaksbehandlerForTotrinn = event.ansvarligSaksbehandlerForTotrinn,
            ansvarligSaksbehandlerIdent = event.ansvarligSaksbehandlerIdent,
            fagsakPeriode = if (event.fagsakPeriode != null) Oppgave.FagsakPeriode(
                event.fagsakPeriode.fom,
                event.fagsakPeriode.tom
            ) else null,
            pleietrengendeAktørId = event.pleietrengendeAktørId,
            relatertPartAktørId = event.relatertPartAktørId,
            kombinert = false
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
        val aktuelleAksjonspunkter = listOf(
            FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS_KODE,
            VURDER_VARIG_ENDRET_ELLER_NYOPPSTARTET_NÆRING_SELVSTENDIG_NÆRINGSDRIVENDE_KODE,
            FASTSETT_BEREGNINGSGRUNNLAG_SELVSTENDIG_NÆRINGSDRIVENDE_KODE,
            FASTSETT_BEREGNINGSGRUNNLAG_FOR_SN_NY_I_ARBEIDSLIVET_KODE,
            VURDER_FAKTA_FOR_ATFL_SN_KODE
        )
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

    @KtorExperimentalAPI
    override fun behandlingOpprettetSakOgBehandling(

    ): BehandlingOpprettet {
        val sisteEvent = sisteEvent()
        val behandlingOpprettet = BehandlingOpprettet(
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
        return behandlingOpprettet
    }

    @KtorExperimentalAPI
    override fun behandlingAvsluttetSakOgBehandling(
    ): BehandlingAvsluttet {
        val sisteEvent = sisteEvent()
        val behandlingAvsluttet = BehandlingAvsluttet(
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
        return behandlingAvsluttet
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
                saksbehandlerRepository.finnSaksbehandlerMedIdentIkkeTaHensyn(reservasjonRepository.hent(oppgave.eksternId).reservertAv!!)
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

    fun bleBeslutter(): Boolean {
        val forrigeEvent = forrigeEvent()
        return forrigeEvent != null && !forrigeEvent.aktiveAksjonspunkt()
            .tilBeslutter() && sisteEvent().aktiveAksjonspunkt().tilBeslutter()
    }

    override fun fikkEndretAksjonspunkt(): Boolean {
        val forrigeEvent = forrigeEvent()
        if (forrigeEvent == null) {
            return false
        }

        if (sisteEvent().ytelseTypeKode == "PSB") {
            val forrigeAksjonspunkter = forrigeEvent.aktiveAksjonspunktEllerAvbrutt().liste
            val nåværendeAksjonspunkter = sisteEvent().aktiveAksjonspunktEllerAvbrutt().liste

            if (sisteEvent().aktiveAksjonspunktEllerAvbrutt()
                    .lengde() > 0 && !sisteEvent().aktiveAksjonspunktEllerAvbrutt().tilBeslutter()
            ) {
                return false
            }

            if (forrigeAksjonspunkter.size == nåværendeAksjonspunkter.size &&
                (forrigeAksjonspunkter.values.contains("AVBR") || nåværendeAksjonspunkter.values.contains("AVBR"))
            ) {
                return false
            }
            return forrigeAksjonspunkter != nåværendeAksjonspunkter
        } else {
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
    return Aksjonspunkter(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == "OPPR" })
}

fun BehandlingProsessEventDto.aktiveAksjonspunktEllerAvbrutt(): Aksjonspunkter {
    return Aksjonspunkter(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == "OPPR" || entry.value == "AVBR" })
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


