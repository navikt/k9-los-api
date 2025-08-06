package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import java.util.*

interface IPepClient {

    suspend fun erOppgaveStyrer(): Boolean

    suspend fun harTilgangTilKode6(): Boolean

    suspend fun harTilgangTilKode6(ident: String): Boolean

    suspend fun harBasisTilgang(): Boolean

    suspend fun kanLeggeUtDriftsmelding(): Boolean

    suspend fun harTilgangTilReserveringAvOppgaver(): Boolean

    suspend fun erSakKode6(
        fagsakNummer: String
    ): Boolean

    suspend fun erSakKode7EllerEgenAnsatt(
        fagsakNummer: String
    ): Boolean

    suspend fun diskresjonskoderForSak(fagsakNummer: String): Set<Diskresjonskode>
    suspend fun diskresjonskoderForPerson(aktørId: String): Set<Diskresjonskode>

    suspend fun erAktørKode6(aktørid: String): Boolean
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean

    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        action: Action,
        auditlogging: Auditlogging,
        grupperForSaksbehandler: Set<UUID>? = null
    ) : Boolean

    fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        saksbehandler: Saksbehandler,
        action: Action,
        auditlogging: Auditlogging
    ) : Boolean
}

enum class Auditlogging {
    IKKE_LOGG,
    ALLTID_LOGG,
    LOGG_VED_PERMIT
}
