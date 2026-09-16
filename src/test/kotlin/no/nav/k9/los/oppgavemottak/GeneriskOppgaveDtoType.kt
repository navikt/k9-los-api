package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.oppgavedefinisjon.omraade.Områdeidentifikator

/**
 * Oppgavetype som ikke er en av de kodede oppgavetypene i [no.nav.k9.los.domeneadaptere.eventtiloppgave].
 *
 * Bevisst scopet til test: produksjonskoden skal kun kunne lage oppgaver av oppgavetyper som er kjent
 * på kompileringstidspunktet (K9Oppgavetypenavn/AktOppgavetypenavn), mens tester kan bygge reduserte
 * oppgavemodeller med egne oppgavetyper — på sitt eget ad hoc-område.
 */
data class GeneriskOppgaveDtoType(
    override val kode: String,
    override val område: Områdeidentifikator,
) : OppgaveDtoType

