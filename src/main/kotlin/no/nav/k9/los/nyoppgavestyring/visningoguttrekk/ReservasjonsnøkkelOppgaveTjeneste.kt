package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

interface ReservasjonsnøkkelOppgaveTjeneste {
    /** Hent alle åpne oppgaver for en gitt reservasjonsnøkkel */
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave>
}
