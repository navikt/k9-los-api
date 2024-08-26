package no.nav.k9.los.domene.modell

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.los.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
import no.nav.k9.statistikk.kontrakter.Behandling
import no.nav.k9.statistikk.kontrakter.Sak

data class K9PunsjModell(
    val eventer: List<PunsjEventDto>
) : IModell {

    override fun starterSak(): Boolean {
        return eventer.size == 1
    }

    override fun erTom(): Boolean {
        return this.eventer.isEmpty()
    }

    override fun dvhSak(): Sak {
        TODO("Ikke relevant for punsj")
    }

    override fun dvhBehandling(
        saksbehandlerRepository: SaksbehandlerRepository,
        reservasjonRepository: ReservasjonRepository
    ): Behandling {
        TODO("Ikke relevant for punsj")
    }

    override fun sisteSaksNummer(): String {
        TODO("Ikke relevant for punsj")
    }

    override fun behandlingOpprettetSakOgBehandling(): BehandlingOpprettet {
        TODO("Ikke relevant for punsj")
    }

    override fun behandlingAvsluttetSakOgBehandling(): BehandlingAvsluttet {
        TODO("Ikke relevant for punsj")
    }

    internal fun forrigeEvent(): PunsjEventDto? {
        return if (this.eventer.lastIndex > 0) {
            this.eventer[this.eventer.lastIndex - 1]
        } else {
            null
        }
    }

    fun sisteEvent(): PunsjEventDto {
        return this.eventer[this.eventer.lastIndex]
    }

    override fun fikkEndretAksjonspunkt(): Boolean {
        val forrigeEvent = forrigeEvent() ?: return false
        val forrigeAksjonspunkter = forrigeEvent.tilAksjonspunkter().hentAktive()
        val nåværendeAksjonspunkter = sisteEvent().tilAksjonspunkter().hentAktive()
        return forrigeAksjonspunkter != nåværendeAksjonspunkter
    }

    fun oppgave(sisteEvent: PunsjEventDto = sisteEvent()): Oppgave {
        val førsteEvent = eventer.first()
        val førsteEventMedJournalførtTidspunktSatt = eventer.firstOrNull { it.journalførtTidspunkt != null }

        var aktiv = sisteEvent.aksjonspunktKoderMedStatusListe.any { aksjonspunkt -> aksjonspunkt.value == "OPPR" }

        if (sisteEvent.tilAksjonspunkter().hentAktive().containsKey("MER_INFORMASJON")) {
            aktiv = false
        }

        return Oppgave(
            behandlingId = null,
            fagsakSaksnummer = "",
            journalpostId = førsteEvent.journalpostId.verdi,
            //må se på siste siden den kan endre seg hvis punsj finner ut at opprinnelig oppgave var på barnets aktør f.eks.
            aktorId = sisteEvent.aktørId?.id ?: "",
            behandlendeEnhet = "",
            behandlingsfrist = førsteEvent.eventTid.toLocalDate().plusDays(21).atStartOfDay(),
            behandlingOpprettet = førsteEvent.eventTid,
            forsteStonadsdag = førsteEvent.eventTid.toLocalDate(),
            behandlingStatus = sisteEvent.utledStatus(),
            behandlingType = (sisteEvent.type ?: førsteEvent.type)?.let { BehandlingType.fraKode(it) } ?: BehandlingType.UKJENT,
            fagsakYtelseType = (sisteEvent.ytelse ?: førsteEvent.ytelse)?.let { FagsakYtelseType.fraKode(it) } ?: FagsakYtelseType.UKJENT,
            eventTid = sisteEvent.eventTid,
            aktiv = aktiv,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = sisteEvent.eksternId,
            oppgaveEgenskap = listOf(),
            aksjonspunkter = sisteEvent.tilAktiveAksjonspunkter(),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            søktGradering = false,
            årskvantum = false,
            avklarMedlemskap = false,
            avklarArbeidsforhold = false,
            kode6 = false,
            skjermet = false,
            utenlands = false,
            vurderopptjeningsvilkåret = false,
            ansvarligSaksbehandlerForTotrinn = null,
            ansvarligSaksbehandlerIdent = null,
            kombinert = false,
            pleietrengendeAktørId = sisteEvent.pleietrengendeAktørId,
            journalførtTidspunkt = førsteEventMedJournalførtTidspunktSatt?.journalførtTidspunkt
        )
    }

    override fun oppgave(): Oppgave {
        return oppgave(sisteEvent())
    }

    fun alleVersjoner(): MutableList<K9PunsjModell> {
        val eventListe = mutableListOf<PunsjEventDto>()
        val modeller = mutableListOf<K9PunsjModell>()
        for (behandlingProsessEventDto in eventer) {
            eventListe.add(behandlingProsessEventDto)
            modeller.add(K9PunsjModell(eventListe.toMutableList()))
        }
        return modeller
    }
}

fun PunsjEventDto.utledStatus() : BehandlingStatus {
    val aksjonspunkter = tilAksjonspunkter()
    if (aksjonspunkter.hentAktive().containsKey("MER_INFORMASJON")) {
        return BehandlingStatus.SATT_PÅ_VENT
    } else if (sendtInn != null && sendtInn) {
        return BehandlingStatus.SENDT_INN
    } else if (aksjonspunkter.erIngenAktive() && (sendtInn == null || !sendtInn)) {
        return BehandlingStatus.LUKKET
    }
    return BehandlingStatus.OPPRETTET
}
