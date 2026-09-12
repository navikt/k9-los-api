package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.UUID

interface ISifAbacPdpKlient {
    suspend fun hentTilganger(idToken: IIdToken): Tilganger

    suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode>
    suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode>

    // For innlogget
    suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto, idToken: IIdToken): Boolean
    suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>, idToken: IIdToken): Boolean

    // For annen saksbehandler
    suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean

    suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean
}
