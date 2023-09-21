package no.nav.k9.los.integrasjon.abac

import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Saksbehandler

interface IPepClient {

    suspend fun erOppgaveStyrer(): Boolean

    suspend fun harTilgangTilKode6(): Boolean

    suspend fun harBasisTilgang(): Boolean

    suspend fun kanLeggeUtDriftsmelding(): Boolean

    suspend fun harTilgangTilLesSak(
        fagsakNummer: String,
        aktørid: String
    ): Boolean

    suspend fun harTilgangTilReservingAvOppgaver(): Boolean

    suspend fun kanSendeSakTilStatistikk(
        fagsakNummer: String
    ): Boolean

    suspend fun erSakKode6(
        fagsakNummer: String
    ): Boolean

    suspend fun erSakKode7EllerEgenAnsatt(
        fagsakNummer: String
    ): Boolean

    suspend fun erAktørKode6(aktørid: String): Boolean
    suspend fun erAktørKode7EllerEgenAnsatt(aktørid: String): Boolean

    suspend fun harTilgangTilOppgave(oppgave: Oppgave) : Boolean

    suspend fun harTilgangTilÅReservereOppgave(oppgave: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave, bruker: Saksbehandler) : Boolean
}
