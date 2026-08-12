package no.nav.k9.los.infrastruktur.idtoken

import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

/**
 * Token tolket for området K9, dvs. med K9 sine AD-grupper.
 *
 * [jwt] kan sendes inn ferdig parset når tokenet rutes fra et annet område, jf.
 * [IdTokenForOmråde.forOmråde].
 */
data class IdTokenK9(
    override val value: String,
    override val jwt: JWTToken = JWTToken.fra(value),
) : IdTokenForOmråde {
    override val område = Områder.K9

    override fun kanBehandleKode6(): Boolean = iGruppe("BRUKER_GRUPPE_ID_KODE6")
    override fun kanBehandleKode7(): Boolean = iGruppe("BRUKER_GRUPPE_ID_KODE7")
    override fun kanBehandleEgneAnsatte(): Boolean = iGruppe("BRUKER_GRUPPE_ID_EGENANSATT")
    override fun erOppgavebehandler(): Boolean = iGruppe("BRUKER_GRUPPE_ID_OPPGAVESTYRER")
    override fun erSaksbehandler(): Boolean = iGruppe("BRUKER_GRUPPE_ID_SAKSBEHANDLER")
    override fun erVeileder(): Boolean = iGruppe("BRUKER_GRUPPE_ID_VEILEDER")
    override fun erDrifter(): Boolean = iGruppe("BRUKER_GRUPPE_ID_DRIFT")
    override fun harBasistilgang(): Boolean = erSaksbehandler() || erVeileder()

    private fun iGruppe(miljøvariabel: String): Boolean =
        jwt.groups.any { gruppe -> gruppe == System.getenv(miljøvariabel)!! }
}

internal fun ApplicationCall.idToken(): IIdToken {
    val jwt = request.parseAuthorizationHeader()?.render() ?: throw IllegalStateException("Token ikke satt")
    return IdTokenK9(jwt.substringAfter("Bearer "))
}
