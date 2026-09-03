package no.nav.k9.los.infrastruktur.abac

import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import java.util.UUID

class SifAbacPdpKlientAktivitetspenger : ISifAbacPdpKlient {

    override suspend fun hentTilganger(idToken: IdToken): Tilganger =
        Tilganger(false, false, false, false, false)

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> =
        emptySet()

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> =
        emptySet()

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        idToken: IdToken,
    ): Boolean = false

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        idToken: IdToken,
    ): Boolean = false

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>,
    ): Boolean = false

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>,
    ): Boolean = false
}
