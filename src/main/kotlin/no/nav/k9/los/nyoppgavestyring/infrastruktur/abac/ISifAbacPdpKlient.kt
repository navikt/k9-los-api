package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import java.util.UUID

interface ISifAbacPdpKlient {
    suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode>
    suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode>

    suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto): Boolean
    suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>): Boolean

    suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto, saksbhandlersGrupper : Set<UUID>): Boolean
    suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>, saksbhandlersGrupper : Set<UUID>): Boolean



}