package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

// Det har aldri vært produksjonsstyring i k9-los for FRISINN, så den skal ignoreres for produksjonsstyringsformål inntil alle hendelser på ytelsen er fjernet fra k9-los
fun gjelderFRISINN(oppgave: OppgaveV3): Boolean {
    return oppgave.felter.any { it.oppgavefelt.feltDefinisjon.kodeverkreferanse?.eksternId == "ytelsetype" && it.verdi == "FRISINN" }
}
