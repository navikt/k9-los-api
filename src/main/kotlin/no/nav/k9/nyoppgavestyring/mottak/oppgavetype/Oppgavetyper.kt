package no.nav.k9.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.nyoppgavestyring.mottak.omraade.Område


class Oppgavetyper(
    val område: Område,
    val oppgavetyper: Set<Oppgavetype>
) {

    constructor(dto: OppgavetyperDto, område: Område, feltdefinisjoner: Feltdefinisjoner): this(
        område = område,
        oppgavetyper = dto.oppgavetyper.map { oppgavetypeDto ->
            Oppgavetype(
                dto = oppgavetypeDto,
                definisjonskilde = dto.definisjonskilde,
                område = område,
                feltdefinisjoner = feltdefinisjoner)
        }.toSet()
    )


    fun finnForskjell(innkommendeOppgavetyper: Oppgavetyper): Triple<Oppgavetyper, Oppgavetyper, List<OppgavetypeEndring>> {
        if (!innkommendeOppgavetyper.område.equals(this.område)) {
            throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av områder")
        }

        val slettListe = mutableSetOf<Oppgavetype>()
        val leggTilListe = mutableSetOf<Oppgavetype>()
        val finnFeltforskjellListe = mutableSetOf<OppgavetypeEndring>()

        innkommendeOppgavetyper.oppgavetyper.forEach { innkommende ->
            val eksisterende = oppgavetyper.find { it.eksternId.equals(innkommende.eksternId) }
            if (eksisterende?.definisjonskilde != null && !eksisterende.definisjonskilde.equals(innkommende.definisjonskilde)) {
                //?. - hvis eksisterende ikke finnes gir det ikke mening å sammenligne, siden det er en ny oppgavetype
                throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av definisjonskilder")
            }
            if (eksisterende == null) {
                leggTilListe.add(innkommende)
            } else {
                finnFeltforskjellListe.add(utledOppdatering(eksisterende, innkommende))
            }
        }

        oppgavetyper.forEach { eksistereende ->
            val innkommende = innkommendeOppgavetyper.oppgavetyper.find { it.eksternId.equals(eksistereende.eksternId) }
            if (innkommende == null) {
                slettListe.add(eksistereende)
            }
        }
        return Triple(
            Oppgavetyper(
                område = this.område,
                oppgavetyper = slettListe.toSet()
            ),
            Oppgavetyper(
                område = this.område,
                oppgavetyper = leggTilListe.toSet()
            ),
            finnFeltforskjellListe.toList()
        )
    }

    fun utledOppdatering(eksisterendeOppgave: Oppgavetype, innkommendeOppgave: Oppgavetype): OppgavetypeEndring {
        val leggTilListe = mutableSetOf<Oppgavefelt>()
        val sletteliste = mutableSetOf<Oppgavefelt>()
        val endreliste = mutableSetOf<OppgavefeltDelta>()
        
        innkommendeOppgave.oppgavefelter.forEach{ innkommendeFelt ->
            val eksisterendeOppgavefelt = eksisterendeOppgave.oppgavefelter.find { oppgavefelt ->
                oppgavefelt.feltDefinisjon.equals(innkommendeFelt.feltDefinisjon)
            }

            if (eksisterendeOppgavefelt == null) {
                leggTilListe.add(innkommendeFelt)
            } else {
                if (!innkommendeFelt.påkrevd.equals(eksisterendeOppgavefelt.påkrevd)) {
                    endreliste.add(OppgavefeltDelta(eksisterendeFelt = eksisterendeOppgavefelt, innkommendeFelt = innkommendeFelt))
                } else if (!innkommendeFelt.visPåOppgave.equals(eksisterendeOppgavefelt.visPåOppgave)) {
                    endreliste.add(OppgavefeltDelta(eksisterendeFelt = eksisterendeOppgavefelt, innkommendeFelt = innkommendeFelt))
                }
            }
        }

        eksisterendeOppgave.oppgavefelter.forEach { eksisterendeFelt ->
            val innkommendeOppgavefelt = innkommendeOppgave.oppgavefelter.find { oppgavefelt ->
                oppgavefelt.feltDefinisjon.equals(eksisterendeFelt.feltDefinisjon)
            }

            if (innkommendeOppgavefelt == null) {
                sletteliste.add(eksisterendeFelt)
            }
        }
        return OppgavetypeEndring(
            oppgavetype = eksisterendeOppgave,
            felterSomSkalLeggesTil = leggTilListe.toList(),
            felterSomSkalFjernes = sletteliste.toList(),
            felterSomSkalEndresMedNyeVerdier = endreliste.toList()

        )
    }
}