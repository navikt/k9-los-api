package no.nav.k9.los.nyoppgavestyring.forvaltning

import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.query.Oppgavefilter

data class Feltreferanse(val område: String?, val kode: String)

internal fun OppgaveQuery.inneholderFelt(område: String?, kode: String): Boolean {
    return filtere.inneholderFelt(område, kode)
}

internal fun OppgaveQuery.hentAlleFeltreferanser(): Set<Feltreferanse> {
    return filtere.hentAlleFeltreferanser()
}

private fun List<Oppgavefilter>.inneholderFelt(område: String?, kode: String): Boolean {
    return any { filter ->
        when (filter) {
            is FeltverdiOppgavefilter -> filter.område == område && filter.kode == kode
            is CombineOppgavefilter -> filter.filtere.inneholderFelt(område, kode)
        }
    }
}

private fun List<Oppgavefilter>.hentAlleFeltreferanser(): Set<Feltreferanse> {
    val referanser = mutableSetOf<Feltreferanse>()
    for (filter in this) {
        when (filter) {
            is FeltverdiOppgavefilter -> referanser.add(Feltreferanse(filter.område, filter.kode))
            is CombineOppgavefilter -> referanser.addAll(filter.filtere.hentAlleFeltreferanser())
        }
    }
    return referanser
}
