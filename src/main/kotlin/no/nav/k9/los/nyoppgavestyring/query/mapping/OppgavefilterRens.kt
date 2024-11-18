package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterRens {
    fun rens(felter:  Map<OmrådeOgKode, OppgavefeltMedMer>, oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere
            .let { OppgavefilterUtenBetingelserFjerner.fjern(it)} // alle filtre har nå minst én verdi (kan være null)
            .let { OppgavefilterFlerverdiEliminerer.eliminer(it) } // alle filtre har nå kun én verdi, og mengdeoperatorer er borte
            .let { OppgavefilterLocalDateSpesialhåndterer.spesialhåndter(it) } // dersom verdien lar seg parse til LocalDate, tilpass filtrene
            .let { OppgavefilterDatatypeMapper.map(felter, it) } // konverter filterverdiene til deres rette datatype
    }
}