package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

data class OppgavefeltDto (
    val id: String,
    val visPåOppgave: Boolean,
    val påkrevd: Boolean,
    val defaultverdi: String? = null,
    val feltutleder: String? = null
)
