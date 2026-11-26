package no.nav.k9.los.oppgaveuthenting.query.db

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.UngOmrådesetup as UpyOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import org.koin.test.get

class OppgaveQueryRepositoryUngTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `oppgavetypefilter viser alle UNG-typer i område og fellesfilter`() {
        get<UpyOmrådesetup>().setup()

        val oppgavetypefelter = get<OppgaveQueryRepository>().hentAlleFelter(Områder.UNGDOMSPROGRAMYTELSEN)
            .filter { it.kode == "oppgavetype" }
        val områdeverdier = oppgavetypefelter.single { it.område == Områder.UNGDOMSPROGRAMYTELSEN }
        val fellesverdier = oppgavetypefelter.single { it.område == null && it.synlighet == Synlighet.SKJULT }

        val ungOppgavetyper = UngOppgavetypenavn.entries.map { it.kode }.toSet()
        val alleOppgavetyper = (
            K9Oppgavetypenavn.entries.map { it.kode } +
                AvpOppgavetypenavn.entries.map { it.kode } +
                ungOppgavetyper
            ).toSet()

        assertThat(områdeverdier.verdiforklaringer.map { it.verdi }.toSet()).isEqualTo(ungOppgavetyper)
        assertThat(fellesverdier.verdiforklaringer.map { it.verdi }.toSet()).isEqualTo(alleOppgavetyper)
    }
}
