package no.nav.k9.integrasjon.gosys

import org.json.JSONObject
import java.time.LocalDate

class OpprettGosysOppgaveRequest(
    private val oppgaveType: GosysKonstanter.OppgaveType,
    private val prioritet: GosysKonstanter.Prioritet,
    private val temaGruppe: GosysKonstanter.TemaGruppe,
    private val fagsaksystem: GosysKonstanter.Fagsaksystem,
    private val fristIDager: Int,
    private val aktiv: LocalDate,
    private val aktørId: String,
    private val enhetsNummer: String,
    private val fagsakId: String
) {
    fun body(): String {
        return JSONObject()
            .put("oppgavetype", oppgaveType.dto)
            .put("prioritet", prioritet.dto)
            .put("behandlingstema", "kategori.behandlingstema.dto")
            .put("temagruppe", temaGruppe.dto)
            .put("fristFerdigstillelse", Virkedager.nVirkedagerFra(fristIDager, aktiv))
            .put("aktivDato", aktiv.toString())
            .put("aktoerId", aktørId)
            .put("tildeltEnhetsnr", enhetsNummer)
            .put("saksreferanse", fagsakId)
            .put("behandlingstype", "kategori.behandlingstype.dto")
            .put("tema", "kategori.tema.dto")
            .put("behandlesAvApplikasjon", fagsaksystem.dto)
            .toString()
    }
}
