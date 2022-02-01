package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingAvsluttet
import no.nav.k9.integrasjon.sakogbehandling.kontrakt.BehandlingOpprettet
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
        val forrigeAksjonspunkter = forrigeEvent.aktiveAksjonspunkt().liste
        val nåværendeAksjonspunkter = sisteEvent().aktiveAksjonspunkt().liste
        return forrigeAksjonspunkter != nåværendeAksjonspunkter
    }

    private fun PunsjEventDto.aktiveAksjonspunkt(): Aksjonspunkter {
        return Aksjonspunkter(this.aksjonspunktKoderMedStatusListe.filter { entry -> entry.value == "OPPR" })
    }

    fun oppgave(sisteEvent: PunsjEventDto = sisteEvent()): Oppgave {
        val førsteEvent = eventer.first()

        var aktiv = sisteEvent.aksjonspunktKoderMedStatusListe.any { aksjonspunkt -> aksjonspunkt.value == "OPPR" }

        if (sisteEvent.aktiveAksjonspunkt().liste.containsKey("MER_INFORMASJON")) {
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
            behandlingStatus = utledStatus(sisteEvent),
            behandlingType = utledBehandlingType(førsteEvent),
            fagsakYtelseType = if(førsteEvent.ytelse != null) FagsakYtelseType.fraKode(førsteEvent.ytelse) else FagsakYtelseType.PPN,
            eventTid = sisteEvent.eventTid,
            aktiv = aktiv,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = sisteEvent.eksternId,
            oppgaveEgenskap = listOf(),
            aksjonspunkter = sisteEvent.aktiveAksjonspunkt(),
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
            pleietrengendeAktørId = sisteEvent.pleietrengendeAktørId
        )
    }

    override fun oppgave(): Oppgave {
        return oppgave(sisteEvent())
    }

    private fun utledStatus(eventDto: PunsjEventDto) : BehandlingStatus {
        if (eventDto.aktiveAksjonspunkt().liste.containsKey("MER_INFORMASJON")) {
            return BehandlingStatus.SATT_PÅ_VENT
        } else if (eventDto.sendtInn != null && eventDto.sendtInn) {
            return BehandlingStatus.SENDT_INN
        } else if (eventDto.aktiveAksjonspunkt().erTom() && (eventDto.sendtInn == null || !eventDto.sendtInn)) {
            return BehandlingStatus.LUKKET
        }
        return BehandlingStatus.OPPRETTET
    }

    private fun utledBehandlingType(eventDto: PunsjEventDto) : BehandlingType {
        if (eventDto.type == null) {
            return BehandlingType.UKJENT
        }
        return BehandlingType.fraKode(eventDto.type)
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
