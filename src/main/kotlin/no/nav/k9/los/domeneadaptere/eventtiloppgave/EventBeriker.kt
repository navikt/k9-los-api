package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker.K9SakSystemKlientInterfaceKludge
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import java.util.UUID

/**
 * Henter opplysninger fra kildesystemene som mangler i selve eventet, og returnerer eventet med
 * disse feltene utfylt. Dette er det eneste steget i event-til-oppgave-pipelinen som gjør I/O –
 * selve mappingen er rene funksjoner.
 *
 * Eventer for samme behandling berikes med felles cache innenfor ett kall, slik at vi ikke gjør
 * ett oppslag pr. event i serien.
 */
class EventBeriker(
    private val k9SakBeriker: K9SakSystemKlientInterfaceKludge,
    private val k9KlageBeriker: K9KlageBerikerInterfaceKludge,
) {
    fun berik(eventer: List<EventLagret>): List<EventLagret> {
        val cache = Oppslagscache()
        return eventer.map { berik(it, cache) }
    }

    fun berik(eventLagret: EventLagret): EventLagret = berik(eventLagret, Oppslagscache())

    private fun berik(eventLagret: EventLagret, cache: Oppslagscache): EventLagret =
        when (eventLagret) {
            is EventLagret.K9Sak -> berikSak(eventLagret, cache)
            is EventLagret.K9Klage -> berikKlage(eventLagret, cache)
            is EventLagret.K9Punsj,
            is EventLagret.K9Tilbake,
            is EventLagret.UngSak -> eventLagret
        }

    /**
     * Avsluttede behandlinger uten fastsatt resultat mangler resultatet i eventet. Gjelder gamle
     * eventer, samt behandlinger som aldri ble committet i k9-sak (rollback).
     */
    private fun berikSak(eventLagret: EventLagret.K9Sak, cache: Oppslagscache): EventLagret.K9Sak {
        val event = eventLagret.eventDto
        if (!trengerResultatoppslag(event)) {
            return eventLagret
        }

        val behandling = cache.behandling(event.eksternId!!) { k9SakBeriker.hentBehandling(it) }
        return eventLagret.beriketMed(event.copy(resultatType = utledResultattype(behandling)))
    }

    private fun trengerResultatoppslag(event: K9SakEventDto): Boolean =
        event.eksternId != null
                && event.behandlingStatus == AVSLUTTET_BEHANDLINGSTATUS
                && event.ytelseTypeKode != FagsakYtelseType.OBSOLETE.kode
                && (event.resultatType ?: BehandlingResultatType.IKKE_FASTSATT.kode) == BehandlingResultatType.IKKE_FASTSATT.kode

    private fun utledResultattype(behandling: BehandlingMedFagsakDto?): String =
        when {
            // Behandlingen finnes ikke i k9-sak, pga. rollback i transaksjonen som skulle opprette den
            behandling == null -> BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode
            behandling.sakstype == FagsakYtelseType.OBSOLETE -> BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode
            else -> behandling.behandlingResultatType.kode
        }

    /** Klageeventer mangler opplysninger som kun finnes på den påklagde saken i k9-sak/k9-klage. */
    private fun berikKlage(eventLagret: EventLagret.K9Klage, cache: Oppslagscache): EventLagret.K9Klage {
        val event = eventLagret.eventDto
        val losOpplysninger = cache.klageopplysningerFraSak(event.saksnummer) { k9KlageBeriker.hentFraK9Sak(it) }

        return eventLagret.beriketMed(
            event.copy(
                påklagdBehandlingType = event.påklagdBehandlingType
                    ?: event.påklagdBehandlingId?.let {
                        cache.klageopplysningerFraKlage(event.eksternId) { k9KlageBeriker.hentFraK9Klage(it) }
                            ?.påklagdBehandlingType
                    },
                pleietrengendeAktørId = losOpplysninger?.pleietrengendeAktørId?.aktørId?.let { AktørId(it.toLong()) },
                utenlandstilsnitt = losOpplysninger?.isUtenlandstilsnitt,
            )
        )
    }

    private class Oppslagscache {
        private val behandlinger = mutableMapOf<UUID, BehandlingMedFagsakDto?>()
        private val fraSak = mutableMapOf<String, LosOpplysningerSomManglerIKlageDto?>()
        private val fraKlage = mutableMapOf<UUID, LosOpplysningerSomManglerHistoriskIKlageDto?>()

        fun behandling(uuid: UUID, hent: (UUID) -> BehandlingMedFagsakDto?) =
            behandlinger.getOrPut(uuid) { hent(uuid) }

        fun klageopplysningerFraSak(saksnummer: String, hent: (String) -> LosOpplysningerSomManglerIKlageDto?) =
            fraSak.getOrPut(saksnummer) { hent(saksnummer) }

        fun klageopplysningerFraKlage(uuid: UUID, hent: (UUID) -> LosOpplysningerSomManglerHistoriskIKlageDto?) =
            fraKlage.getOrPut(uuid) { hent(uuid) }
    }

    companion object {
        private const val AVSLUTTET_BEHANDLINGSTATUS = "AVSLU"
    }
}

