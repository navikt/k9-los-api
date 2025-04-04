package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Saksbehandler

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

    suspend fun erAktørKode6(aktørid: String): Boolean
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean

    suspend fun harTilgangTilOppgave(oppgave: Oppgave) : Boolean

    suspend fun harTilgangTilOppgaveV3(
        oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave,
        action: Action,
        auditlogging: Auditlogging
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
