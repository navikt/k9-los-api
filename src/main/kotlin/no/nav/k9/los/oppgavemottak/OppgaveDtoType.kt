package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.oppgavedefinisjon.omraade.Områdeidentifikator
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

/**
 * Oppgavetypen en [OppgaveDto] har, sammen med området den hører til.
 *
 * Implementasjonene i produksjon er K9Oppgavetypenavn og AktOppgavetypenavn, som begge snevrer
 * [område] inn til [Områder]. Produksjonskoden kan derfor bare lage oppgaver av oppgavetyper — og på
 * områder — som er kjent på kompileringstidspunktet.
 *
 * [område] er typet som [Områdeidentifikator] og ikke [Områder] fordi testkoden bygger reduserte
 * oppgavemodeller på sine egne ad hoc-områder (se `GeneriskOppgaveDtoType` i testkoden). Alt
 * konsumentene i produksjon trenger herfra er eksternId.
 */
interface OppgaveDtoType {
    val kode: String
    val område: Områdeidentifikator

    companion object {
        fun fra(område: Områder, eksternId: String): OppgaveDtoType = when (område) {
            Områder.K9 -> K9Oppgavetypenavn.fraKode(eksternId)
            Områder.AKTIVITETSPENGER -> AktOppgavetypenavn.fraKode(eksternId)
        }

        fun fraEksternId(områdeEksternId: String, eksternId: String): OppgaveDtoType =
            fra(Områder.fraEksternId(områdeEksternId), eksternId)
    }
}



