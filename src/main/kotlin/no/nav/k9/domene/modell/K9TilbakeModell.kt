package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.AksjonspunktTilstand
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
import no.nav.k9.statistikk.kontrakter.Aktør
import no.nav.k9.statistikk.kontrakter.Behandling
import no.nav.k9.statistikk.kontrakter.Sak
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.min

data class K9TilbakeModell(
    val eventer: List<BehandlingProsessEventTilbakeDto>
) : IModell {
    private val `Omsorgspenger, Pleiepenger og opplæringspenger` = "ab0271"

    override fun oppgave():Oppgave {
        return oppgave(sisteEvent())
    }

    fun oppgave(sisteEvent: BehandlingProsessEventTilbakeDto): Oppgave {
        val aktiveAksjonspunkt = sisteEvent.aktiveAksjonspunkt()
        val eventResultat = aktiveAksjonspunkt.eventResultatTilbake()
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
            behandlingsfrist = sisteEvent.eventTid.plusDays(21),
            behandlingStatus = BehandlingStatus.fraKode(behandlingStatus),
            eksternId = sisteEvent.eksternId ?: UUID.randomUUID(),
            behandlingOpprettet = sisteEvent.opprettetBehandling,
            oppgaveAvsluttet = oppgaveAvsluttet,
            system = sisteEvent.fagsystem,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(aktiveAksjonspunkt.liste, aktiveAksjonspunkt.liste.map { AksjonspunktTilstand(it.key, AksjonspunktStatus.fraKode(it.value)) }),
            utenlands = false,
            tilBeslutter = eventResultat.beslutterOppgave(),
            selvstendigFrilans = false,
            søktGradering = false,
            utbetalingTilBruker = false,
            kode6 = false,
            årskvantum = false,
            avklarMedlemskap = false,
            avklarArbeidsforhold = false,
            vurderopptjeningsvilkåret = false,
            eventTid = sisteEvent.eventTid,
            ansvarligSaksbehandlerForTotrinn = null,
            ansvarligSaksbehandlerIdent = sisteEvent.ansvarligSaksbehandlerIdent,
            kombinert = false
        )
    }

    override fun behandlingOpprettetSakOgBehandling(): BehandlingOpprettet {
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
                `Omsorgspenger, Pleiepenger og opplæringspenger`,
                `Omsorgspenger, Pleiepenger og opplæringspenger`,
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

    fun sisteEvent(): BehandlingProsessEventTilbakeDto {
        return this.eventer[this.eventer.lastIndex]
    }

    fun forrigeEvent(): BehandlingProsessEventTilbakeDto? {
        return if (this.eventer.lastIndex > 0) {
            this.eventer[this.eventer.lastIndex - 1]
        } else {
            null
        }
    }

    fun førsteEvent(): BehandlingProsessEventTilbakeDto {
        return this.eventer[0]
    }

    override fun starterSak(): Boolean {
        return this.eventer.size == 1
    }

    override fun erTom(): Boolean {
        return this.eventer.isEmpty()
    }

    override fun fikkEndretAksjonspunkt(): Boolean {
        val forrigeEvent = forrigeEvent() ?: return false

        // har blitt beslutter
        if (!forrigeEvent.aktiveAksjonspunkt().tilBeslutterTilbake() &&
            sisteEvent().aktiveAksjonspunkt().tilBeslutterTilbake()) {
            return true
        }
        // har blitt satt på vent
        if (sisteEvent().aktiveAksjonspunkt().påVentTilbake()) {
            return true
        }

        // beslutter har gjort seg ferdig
        if (forrigeEvent.aktiveAksjonspunkt().tilBeslutterTilbake() &&
            sisteEvent().InaktiveAksjonspunkt().tilBeslutterTilbake())
         {
            return true
        }
        // skal fortsette og ligge reservert
        return false
    }

    // Array med alle versjoner av modell basert på eventene, brukes når man skal spille av eventer
    fun alleVersjoner(): MutableList<K9TilbakeModell> {
        val eventListe = mutableListOf<BehandlingProsessEventTilbakeDto>()
        val modeller = mutableListOf<K9TilbakeModell>()
        for (behandlingProsessEventDto in eventer) {
            eventListe.add(behandlingProsessEventDto)
            modeller.add(K9TilbakeModell(eventListe.toMutableList()))
        }
        return modeller
    }

    override fun dvhSak(): Sak {
        val oppgave = oppgave(sisteEvent = sisteEvent())
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

    override fun sisteSaksNummer(): String {
        return sisteEvent().saksnummer
    }

    override fun dvhBehandling(
        saksbehandlerRepository: SaksbehandlerRepository,
        reservasjonRepository: ReservasjonRepository
    ): Behandling {
        val oppgave = oppgave(sisteEvent())
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
            avsender = "K9los",
            versjon = 1
        )
    }
}

fun BehandlingProsessEventTilbakeDto.aktiveAksjonspunkt(): AksjonspunkterTilbake {
    return AksjonspunkterTilbake(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == "OPPR" })
}

fun BehandlingProsessEventTilbakeDto.InaktiveAksjonspunkt(): AksjonspunkterTilbake {
    return AksjonspunkterTilbake(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value != "OPPR" })
}

data class AksjonspunkterTilbake(val liste: Map<String, String>) {
    fun lengde(): Int {
        return liste.size
    }

    fun erTom(): Boolean {
        return this.liste.isEmpty()
    }

    fun eventResultatTilbake(): EventResultat {
        if (erTom()) {
            return EventResultat.LUKK_OPPGAVE
        }

        if (påVentTilbake()) {
            return EventResultat.LUKK_OPPGAVE_VENT
        }

        if (tilBeslutterTilbake()) {
            return EventResultat.OPPRETT_BESLUTTER_OPPGAVE
        }

        return EventResultat.OPPRETT_OPPGAVE
    }

    fun påVentTilbake(): Boolean {
        return this.liste.any {
            when (it.key) {
                "7001", "7002" -> true
                else -> false
            }
        }
    }

    fun tilBeslutterTilbake(): Boolean {
        return this.liste.any {
            when (it.key) {
                "5005" -> true
                else -> false
            }
        }
    }
}


