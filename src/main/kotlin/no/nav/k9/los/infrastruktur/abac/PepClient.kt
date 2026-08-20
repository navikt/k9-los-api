package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.BrukerkontekstUtenOmråde
import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import no.nav.k9.los.infrastruktur.kontekst.Områdekontekst
import no.nav.k9.los.infrastruktur.kontekst.Systemkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class PepClient internal constructor(
    private val azureGraphService: IAzureGraphService,
    private val sifAbacPdpKlienter: SifAbacPdpKlienter,
    private val gruppeoppsett: Gruppeoppsett = Gruppeoppsett()
) : IPepClient {
    private val log: Logger = LoggerFactory.getLogger(PepClient::class.java)

    override suspend fun erOppgaveStyrer(kontekst: Brukerkontekst): Boolean {
        return iGruppe(gruppeoppsett.forOmråde(kontekst.område).oppgavestyrer, kontekst.bruker)
    }

    override suspend fun harBasisTilgang(kontekst: Brukerkontekst): Boolean {
        val grupper = gruppeoppsett.forOmråde(kontekst.område)
        return iGruppe(grupper.saksbehandler, kontekst.bruker) || iGruppe(grupper.veileder, kontekst.bruker)
    }

    override suspend fun harBasisTilgangIEttEllerFlereOmråder(bruker: InnloggetBruker): Boolean {
        return Områder.entries.any { område ->
            val grupper = gruppeoppsett.forOmråde(område)
            iGruppe(grupper.saksbehandler, bruker) || iGruppe(grupper.veileder, bruker)
        }
    }

    override suspend fun kanLeggeUtDriftsmelding(kontekst: BrukerkontekstUtenOmråde): Boolean {
        // Drift-gruppen er global for Los og ikke knyttet til området ruten kjører under.
        return iGruppe(gruppeoppsett.drift, kontekst.bruker)
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(kontekst: Brukerkontekst): Boolean {
        return iGruppe(gruppeoppsett.forOmråde(kontekst.område).saksbehandler, kontekst.bruker)
    }

    override suspend fun harTilgangTilKode6(ident: String, kontekst: Brukerkontekst): Boolean {
        if (ident == kontekst.bruker.navIdent) {
            return harTilgangTilKode6(kontekst)
        }
        val kode6Gruppe = gruppeoppsett.forOmråde(kontekst.område).kode6 ?: return false
        val grupper = azureGraphService.hentGrupperForSaksbehandler(ident)
        return grupper.contains(kode6Gruppe)
    }

    override suspend fun harTilgangTilKode6(kontekst: Brukerkontekst): Boolean {
        return iGruppe(gruppeoppsett.forOmråde(kontekst.område).kode6, kontekst.bruker)
    }

    override suspend fun erKode6Bruker(kontekst: BrukerkontekstUtenOmråde): Boolean {
        return Områder.entries.any { område -> iGruppe(gruppeoppsett.forOmråde(område).kode6, kontekst.bruker) }
    }

    private fun iGruppe(gruppeId: UUID?, bruker: InnloggetBruker): Boolean =
        gruppeId?.let(bruker.grupper::contains) ?: false

    override suspend fun diskresjonskoderForSak(fagsakNummer: String, kontekst: Områdekontekst): Set<Diskresjonskode> {
        krevTilgjengelig(kontekst.område)
        return sifAbacPdpKlienter.forOmråde(kontekst.område).diskresjonskoderSak(SaksnummerDto(fagsakNummer))
    }

    override suspend fun diskresjonskoderForPerson(aktørId: String, kontekst: Områdekontekst): Set<Diskresjonskode> {
        krevTilgjengelig(kontekst.område)
        return sifAbacPdpKlienter.forOmråde(kontekst.område).diskresjonskoderPerson(AktørId(aktørId))
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: Brukerkontekst,
        action: Action,
        grupperForSaksbehandler: Set<UUID>?,
    ): Boolean {
        return harTilgang(
            område = kontekst.område,
            oppgavetype = oppgave.oppgavetype.eksternId,
            identTilInnloggetBruker = kontekst.bruker.navIdent,
            action = action,
            saksnummer = oppgave.hentVerdi("saksnummer"),
            aktørIdSøker = oppgave.hentVerdi("aktorId"),
            aktørIdPleietrengende = oppgave.hentVerdi("pleietrengendeAktorId"),
            grupperForSaksbehandler = grupperForSaksbehandler
        )
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: Oppgave,
        kontekst: Systemkontekst,
        saksbehandler: Saksbehandler,
        action: Action
    ): Boolean {
        return harTilgang(
                område = kontekst.område,
                oppgavetype = oppgave.oppgavetype.eksternId,
                identTilInnloggetBruker = saksbehandler.navident!!,
                action = action,
                saksnummer = oppgave.hentVerdi("saksnummer"),
                aktørIdSøker = oppgave.hentVerdi("aktorId"),
                aktørIdPleietrengende = oppgave.hentVerdi("pleietrengendeAktorId"),
            )
    }

    private suspend fun harTilgang(
        område: Områder,
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
        grupperForSaksbehandler: Set<UUID>? = null
    ): Boolean = when (område) {
        Områder.K9 -> harTilgangK9(
            oppgavetype = oppgavetype,
            identTilInnloggetBruker = identTilInnloggetBruker,
            action = action,
            saksnummer = saksnummer,
            aktørIdSøker = aktørIdSøker,
            aktørIdPleietrengende = aktørIdPleietrengende,
            grupperForSaksbehandler = grupperForSaksbehandler
        )

        Områder.UNG -> false
    }

    private suspend fun harTilgangK9(
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
        grupperForSaksbehandler: Set<UUID>? = null
    ): Boolean {
        val klient = sifAbacPdpKlienter.forOmråde(Områder.K9)
        return when (oppgavetype) {
            "k9sak", "k9klage", "k9tilbake" -> {
                //TODO når abac-k9 er ryddet bort: vurder å bruk sifAbacPdpKlient.harTilgangTilSak(action, saksnummer) de steder hvor vi sjekker innlogget bruker
                val saksbehandlersGrupper =
                    grupperForSaksbehandler ?: azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
                val tilgang = klient.harTilgangTilSak(
                    action = action,
                    saksnummerDto = SaksnummerDto(saksnummer!!),
                    saksbehandlersIdent = identTilInnloggetBruker,
                    saksbehandlersGrupper = saksbehandlersGrupper
                )

                tilgang
            }

            "k9punsj" -> {
                val berørteAktørId = setOfNotNull(aktørIdSøker, aktørIdPleietrengende)
                val aktørIder = berørteAktørId.map { AktørId(it) }
                val saksbehandlersGrupper =
                    grupperForSaksbehandler ?: azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
                val tilgang = if (aktørIder.isNotEmpty()) klient.harTilgangTilPersoner(
                    action = action,
                    aktørIder = aktørIder,
                    saksbehandlersIdent = identTilInnloggetBruker,
                    saksbehandlersGrupper = saksbehandlersGrupper
                ) else {
                    log.warn("Ingen aktørIder funnet for punsj-oppgave. Gir som fallback tilgang til oppgaven, for å unngå at den havner utenfor alle køer.")
                    true
                }

                tilgang
            }

            else -> throw NotImplementedError("Støtter kun tilgangsoppslag på k9klage, k9sak, k9tilbake og k9punsj")
        }
    }

    private fun krevTilgjengelig(område: Områder) {
        if (område == Områder.UNG) throw OmrådeIkkeTilgjengeligException(område)
    }

}
