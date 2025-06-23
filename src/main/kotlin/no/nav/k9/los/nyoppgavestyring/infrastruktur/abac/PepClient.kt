package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.nyoppgavestyring.infrastruktur.audit.K9Auditlogger
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.*
import kotlin.coroutines.coroutineContext

class PepClient(
    private val azureGraphService: IAzureGraphService,
    private val k9Auditlogger: K9Auditlogger,
    private val sifAbacPdpKlient: ISifAbacPdpKlient
) : IPepClient {

    override suspend fun erOppgaveStyrer(): Boolean {
        //TODO inline metode
        return coroutineContext.idToken().erOppgavebehandler()
    }

    override suspend fun harBasisTilgang(): Boolean {
        //TODO inline metode
        return coroutineContext.idToken().harBasistilgang()
    }

    override suspend fun kanLeggeUtDriftsmelding(): Boolean {
        //TODO inline metode
        return coroutineContext.idToken().erDrifter()
    }

    override suspend fun harTilgangTilReserveringAvOppgaver(): Boolean {
        //TODO inline metode
        return coroutineContext.idToken().erSaksbehandler()
    }

    override suspend fun harTilgangTilKode6(ident: String): Boolean {
        if (ident == coroutineContext.idToken().getNavIdent()) {
            return harTilgangTilKode6();
        }
        val grupper = azureGraphService.hentGrupperForSaksbehandler(ident)
        return grupper.contains(UUID.fromString(System.getenv("BRUKER_GRUPPE_ID_KODE6")))
    }

    override suspend fun harTilgangTilKode6(): Boolean {
        //TODO inline metode
        return coroutineContext.idToken().kanBehandleKode6()
    }

    override suspend fun erSakKode6(fagsakNummer: String): Boolean {
        val diskresjonskoder = sifAbacPdpKlient.diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        return diskresjonskoder.contains(Diskresjonskode.KODE6)
    }

    override suspend fun erAktørKode6(aktørid: String): Boolean {
        val diskresjonskoder = sifAbacPdpKlient.diskresjonskoderPerson(AktørId(aktørid))
        return diskresjonskoder.contains(Diskresjonskode.KODE6)
    }

    override suspend fun erSakKode7EllerEgenAnsatt(fagsakNummer: String): Boolean {
        val diskresjonskoder = sifAbacPdpKlient.diskresjonskoderSak(SaksnummerDto(fagsakNummer))
        return diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
    }

    override suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean {
        val diskresjonskoder = sifAbacPdpKlient.diskresjonskoderPerson(AktørId(aktørid))
        return diskresjonskoder.contains(Diskresjonskode.KODE7) || diskresjonskoder.contains(Diskresjonskode.SKJERMET)
    }

    override suspend fun harTilgangTilOppgave(oppgave: Oppgave): Boolean {
        val oppgavetype = if (oppgave.behandlingType.gjelderPunsj()) {
            "k9punsj"
        } else {
            "k9sak"
        }
        return harTilgang(
            oppgavetype,
            azureGraphService.hentIdentTilInnloggetBruker(),
            Action.read,
            oppgave.fagsakSaksnummer,
            oppgave.aktorId,
            oppgave.pleietrengendeAktørId,
            Auditlogging.IKKE_LOGG
        )
    }

    override suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        action: Action,
        auditlogging: Auditlogging
    ): Boolean {
        return harTilgang(
            oppgave.oppgavetype.eksternId,
            azureGraphService.hentIdentTilInnloggetBruker(),
            action,
            oppgave.hentVerdi("saksnummer"),
            oppgave.hentVerdi("aktorId"),
            oppgave.hentVerdi("pleietrengendeAktorId"),
            auditlogging
        )
    }

    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        auditlogging: Auditlogging
    ): Boolean {
        return harTilgang(
            oppgave.oppgavetype.eksternId,
            saksbehandler.brukerIdent!!,
            action,
            oppgave.hentVerdi("saksnummer"),
            oppgave.hentVerdi("aktorId"),
            oppgave.hentVerdi("pleietrengendeAktorId"),
            auditlogging
        )
    }

    private suspend fun harTilgang(
        oppgavetype: String,
        identTilInnloggetBruker: String,
        action: Action,
        saksnummer: String?,
        aktørIdSøker: String?,
        aktørIdPleietrengende: String?,
        auditlogging: Auditlogging
    ): Boolean {
        return when (oppgavetype) {
            "k9sak", "k9klage", "k9tilbake" -> {
                //TODO når abac-k9 er ryddet bort: vurder å bruk sifAbacPdpKlient.harTilgangTilSak(action, saksnummer) de steder hvor vi sjekker innlogget bruker
                val saksbehandlersGrupper = azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
                val tilgang = sifAbacPdpKlient.harTilgangTilSak(
                    action,
                    SaksnummerDto(saksnummer!!),
                    identTilInnloggetBruker,
                    saksbehandlersGrupper
                )

                k9Auditlogger.betingetLogging(tilgang, auditlogging) {
                    loggTilgangK9Sak(saksnummer!!, aktørIdSøker!!, identTilInnloggetBruker, action, tilgang)
                }

                tilgang
            }

            "k9punsj" -> {
                val berørteAktørId = setOfNotNull(aktørIdSøker, aktørIdPleietrengende)
                val aktørIder = berørteAktørId.map { AktørId(it) }
                val saksbehandlersGrupper = azureGraphService.hentGrupperForSaksbehandler(identTilInnloggetBruker)
                val tilgang = sifAbacPdpKlient.harTilgangTilPersoner(
                    action,
                    aktørIder,
                    identTilInnloggetBruker,
                    saksbehandlersGrupper
                )

                berørteAktørId.firstOrNull()?.let { aktørId ->
                    k9Auditlogger.betingetLogging(tilgang, auditlogging) {
                        loggTilgangK9Punsj(aktørId, identTilInnloggetBruker, action, tilgang)
                    }
                }

                tilgang
            }

            else -> throw NotImplementedError("Støtter kun tilgangsoppslag på k9klage, k9sak, k9tilbake og k9punsj")
        }
    }

}

