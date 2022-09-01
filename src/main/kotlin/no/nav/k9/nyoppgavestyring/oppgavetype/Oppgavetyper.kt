package no.nav.k9.nyoppgavestyring.oppgavetype

import no.nav.k9.nyoppgavestyring.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.nyoppgavestyring.omraade.Område


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


    fun finnForskjell(innkommendeOppgavetyper: Oppgavetyper): Triple<Oppgavetyper, Oppgavetyper, Oppgavetyper> {
        if (!innkommendeOppgavetyper.område.equals(this.område)) {
            throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av områder")
        }

        val slettListe = mutableSetOf<Oppgavetype>()
        val leggTilListe = mutableSetOf<Oppgavetype>()
        val finnFeltforskjellListe = mutableSetOf<Oppgavetype>()

        innkommendeOppgavetyper.oppgavetyper.forEach { innkommende ->
            val eksisterende = oppgavetyper.find { it.eksternId.equals(innkommende.eksternId) }
            if (eksisterende?.definisjonskilde != null && !eksisterende.definisjonskilde.equals(innkommende.definisjonskilde)) {
                //?. - hvis eksisterende ikke finnes gir det ikke mening å sammenligne, siden det er en ny oppgavetype
                throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av definisjonskilder")
            }
            if (eksisterende == null) {
                leggTilListe.add(innkommende)
            } else {
                //finnFeltforskjellListe.add(innkommende)
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
            Oppgavetyper(
                område = this.område,
                oppgavetyper = finnFeltforskjellListe.toSet()
            )
        )
    }
}