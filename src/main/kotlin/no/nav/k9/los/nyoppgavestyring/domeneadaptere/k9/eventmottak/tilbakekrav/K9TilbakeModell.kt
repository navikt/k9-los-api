package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.AksjonspunktTilstand
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.domene.modell.EventResultat
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.kontrakt.BehandlingAvsluttet
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.kontrakt.BehandlingOpprettet
import no.nav.k9.los.nyoppgavestyring.kodeverk.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDateTime
import java.util.*
import kotlin.math.min

data class K9TilbakeModell(
    val eventer: List<K9TilbakeEventDto>
) {
    private val `Omsorgspenger, Pleiepenger og opplæringspenger` = "ab0271"

    fun oppgave(): Oppgave {
        return oppgave(sisteEvent())
    }

    fun oppgave(sisteEvent: K9TilbakeEventDto): Oppgave {
        val aktiveAksjonspunkt = sisteEvent.aktiveAksjonspunkt()
        val eventResultat = aktiveAksjonspunkt.eventResultatTilbake()
        var aktiv = true
        var oppgaveAvsluttet: LocalDateTime? = null

        if (eventResultat.lukkerOppgave()) {
            aktiv = false
            oppgaveAvsluttet = sisteEvent.eventTid
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
            ansvarligSaksbehandlerForTotrinn = sisteEvent.ansvarligSaksbehandlerIdent,
            ansvarligSaksbehandlerIdent = sisteEvent.ansvarligSaksbehandlerIdent,
            ansvarligBeslutterForTotrinn = sisteEvent.ansvarligBeslutterIdent,
            kombinert = false,
            feilutbetaltBeløp = sisteEvent.feilutbetaltBeløp
        )
    }

    fun behandlingOpprettetSakOgBehandling(): BehandlingOpprettet {
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

    fun behandlingAvsluttetSakOgBehandling(
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

    fun sisteEvent(): K9TilbakeEventDto {
        return this.eventer[this.eventer.lastIndex]
    }

    fun starterSak(): Boolean {
        return this.eventer.size == 1
    }
}

fun K9TilbakeEventDto.aktiveAksjonspunkt(): AksjonspunkterTilbake {
    return AksjonspunkterTilbake(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == "OPPR" })
}

data class AksjonspunkterTilbake(val liste: Map<String, String>) {
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
        //burde egentlig sjekket at behandling er i FVED-status og har 5005-aksjonspunktet (fatte vedtak)
        return this.liste.containsKey("5005")
                && liste.size == 1 //hvis det er flere aksjonspunkter, er det noe saksbehandler skal gjøre før beslutter løser 5005
    }
}
