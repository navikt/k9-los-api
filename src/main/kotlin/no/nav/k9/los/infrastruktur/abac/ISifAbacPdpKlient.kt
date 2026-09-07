package no.nav.k9.los.infrastruktur.abac

import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.*

interface ISifAbacPdpKlient {
    suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode>
    suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto, saksbehandlersIdent : String): Boolean
    suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>, saksbehandlersIdent : String): Boolean
}