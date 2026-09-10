package no.nav.k9.los.infrastruktur.abac

import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.UUID

// Kun for lokal- og testprofil, aldri som reserve ved PDP-feil.
class SifAbacPdpKlientLocal : ISifAbacPdpKlient {
    override suspend fun hentTilganger(idToken: IIdToken): Tilganger = Tilganger(
        basis = true,
        kode6 = false,
        oppgavestyring = true,
        reservering = true,
        drift = true,
    )

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> = emptySet()
    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> = emptySet()

    override suspend fun harTilgangTilSak(
        action: Action, saksnummerDto: SaksnummerDto, idToken: IIdToken,
    ): Boolean = true

    override suspend fun harTilgangTilPersoner(
        action: Action, aktørIder: List<AktørId>, idToken: IIdToken,
    ): Boolean = true

    override suspend fun harTilgangTilSak(
        action: Action, saksnummerDto: SaksnummerDto, saksbehandlersIdent: String, saksbehandlersGrupper: Set<UUID>,
    ): Boolean = true

    override suspend fun harTilgangTilPersoner(
        action: Action, aktørIder: List<AktørId>, saksbehandlersIdent: String, saksbehandlersGrupper: Set<UUID>,
    ): Boolean = true
}
