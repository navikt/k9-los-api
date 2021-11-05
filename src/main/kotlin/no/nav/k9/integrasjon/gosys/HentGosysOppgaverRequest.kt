package no.nav.k9.integrasjon.gosys

data class HentGosysOppgaverRequest(
    val tema: GosysKonstanter.Tema,
    val oppgaveType: GosysKonstanter.OppgaveType,
    val aktørId: String
){
    fun queryString(): String {
        return "?tema=${tema.dto}&oppgavetype=${oppgaveType.dto}&aktoerId=$aktørId"
    }
}
