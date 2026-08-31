package no.nav.k9.los.infrastruktur.abac

import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.UUID

class SifAbacPdpKlientAktivitetspenger : ISifAbacPdpKlient {

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> =
        ikkeImplementert("diskresjonskoderSak")

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
    ): Boolean = ikkeImplementert("harTilgangTilSak(grupper)")

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
    ): Boolean = ikkeImplementert("harTilgangTilPersoner(grupper)")

    private fun ikkeImplementert(operasjon: String): Nothing =
        throw NotImplementedError("Tilgangskontroll for område AKTIVITETSPENGER er ikke implementert ennå ($operasjon)")
}

