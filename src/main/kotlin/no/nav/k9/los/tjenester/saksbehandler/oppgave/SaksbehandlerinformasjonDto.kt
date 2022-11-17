package no.nav.k9.los.tjenester.saksbehandler.oppgave

class SaksbehandlerinformasjonDto(
    private val saksbehandlerIdent: String,
    val navn: String,
    private val avdelinger: List<String>
) {

    override fun toString(): String {
        return "SaksbehandlerinformasjonDto{" +
                ", saksbehandlerIdent='" + saksbehandlerIdent + '\'' +
                ", navn='" + navn + '\'' +
                ", avdelinger=" + avdelinger +
                '}'
    }

}
