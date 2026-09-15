package no.nav.k9.los.infrastruktur.abac

import kotlinx.coroutines.withTimeoutOrNull
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRuter
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

data class TilgangerCacheKey(
    val område: Områder,
    val navIdent: String,
    val tokenId: String,
) {
    constructor(område: Områder, idToken: IIdToken) : this(område, idToken.getNavIdent(), idToken.getTokenId())
}

class SifAbacPdpKlient(
    sifAbacPdpKlientK9: ISifAbacPdpKlient,
    sifAbacPdpKlientAktivitetspenger: ISifAbacPdpKlient,
    private val hentTilgangerTimeout: kotlin.time.Duration = 5.seconds,
) : ISifAbacPdpKlient {
    val log: Logger = LoggerFactory.getLogger("SifAbacPdpKlient")
    private val tilgangerCache = Cache<TilgangerCacheKey, Tilganger>(300)
    private val klientRuter = OmrådeRuter(sifAbacPdpKlientK9, sifAbacPdpKlientAktivitetspenger)

    override suspend fun hentTilganger(idToken: IIdToken): Tilganger {
        return hentTilganger(coroutineContext.område(), idToken)
    }

    suspend fun hentTilganger(område: Områder, idToken: IIdToken): Tilganger {
        return tilgangerCache.hentSuspend(TilgangerCacheKey(område, idToken), Duration.ofMinutes(60)) {
            withTimeoutOrNull(hentTilgangerTimeout) {
                klientRuter.forOmråde(område).hentTilganger(idToken)
            } ?: throw SifAbacPdpUtilgjengeligException()
        }
    }

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> {
        return diskresjonskoderPerson(coroutineContext.område(), aktørId)
    }

    suspend fun diskresjonskoderPerson(område: Områder, aktørId: AktørId): Set<Diskresjonskode> =
        klientRuter.forOmråde(område).diskresjonskoderPerson(aktørId)

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> {
        return diskresjonskoderSak(coroutineContext.område(), saksnummerDto)
    }

    suspend fun diskresjonskoderSak(område: Områder, saksnummerDto: SaksnummerDto): Set<Diskresjonskode> =
        klientRuter.forOmråde(område).diskresjonskoderSak(saksnummerDto)

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        idToken: IIdToken
    ): Boolean {
        return harTilgangTilSak(coroutineContext.område(), action, saksnummerDto, idToken)
    }

    suspend fun harTilgangTilSak(
        område: Områder,
        action: Action,
        saksnummerDto: SaksnummerDto,
        idToken: IIdToken,
    ): Boolean = klientRuter.forOmråde(område).harTilgangTilSak(action, saksnummerDto, idToken)

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        idToken: IIdToken
    ): Boolean {
        return harTilgangTilPersoner(coroutineContext.område(), action, aktørIder, idToken)
    }

    suspend fun harTilgangTilPersoner(
        område: Områder,
        action: Action,
        aktørIder: List<AktørId>,
        idToken: IIdToken,
    ): Boolean = klientRuter.forOmråde(område).harTilgangTilPersoner(action, aktørIder, idToken)

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>,
    ): Boolean = harTilgangTilSak(
        coroutineContext.område(),
        action,
        saksnummerDto,
        saksbehandlersIdent,
        saksbehandlersGrupper,
    )

    suspend fun harTilgangTilSak(
        område: Områder,
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>,
    ): Boolean = klientRuter.forOmråde(område).harTilgangTilSak(
        action,
        saksnummerDto,
        saksbehandlersIdent,
        saksbehandlersGrupper,
    )

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>,
    ): Boolean = harTilgangTilPersoner(
        coroutineContext.område(),
        action,
        aktørIder,
        saksbehandlersIdent,
        saksbehandlersGrupper,
    )

    suspend fun harTilgangTilPersoner(
        område: Områder,
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>,
    ): Boolean = klientRuter.forOmråde(område).harTilgangTilPersoner(
        action,
        aktørIder,
        saksbehandlersIdent,
        saksbehandlersGrupper,
    )
}
