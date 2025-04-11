package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId

class SifAbacPdpKlientDummy : ISifAbacPdpKlient{
    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> {
        return emptySet()
    }

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> {
        return emptySet()
    }

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto): Boolean {
        return true
    }

    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>): Boolean {
        return true
    }
}