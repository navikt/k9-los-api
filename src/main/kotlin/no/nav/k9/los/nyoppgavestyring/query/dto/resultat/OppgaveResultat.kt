package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId

data class OppgaveResultat(
    val id: EksternOppgaveId,
    val felter: List<Oppgavefeltverdi>
) {
    fun hentVerdi(område: String?, kode: String): Any? =
        felter.firstOrNull { it.område == område && it.kode == kode }?.verdi
}
