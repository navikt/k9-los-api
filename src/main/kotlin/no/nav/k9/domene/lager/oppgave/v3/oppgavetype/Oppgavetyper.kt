package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjoner

class Oppgavetyper(
    val område: String,
    val oppgavetyper: Set<Oppgavetype>
) {

    constructor(dto: OppgavetyperDto, feltdefinisjoner: Feltdefinisjoner): this(
        område = dto.område,
        oppgavetyper = dto.oppgavetyper.map {
            Oppgavetype(
                it,
                dto.definisjonskilde,
                feltdefinisjoner)
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
            val eksisterende = oppgavetyper.find { it.id.equals(innkommende.id) }
            if (!eksisterende?.definisjonskilde.equals(innkommende.definisjonskilde)) {
                //?. - hvis eksisterende ikke finnes gir det ikke mening å sammenligne, siden det er en ny oppgavetype
                throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av definisjonskilder")
            }
            if (eksisterende == null) {
                leggTilListe.add(innkommende)
            } else {
                finnFeltforskjellListe.add(innkommende)
            }
        }

        oppgavetyper.forEach { eksistereende ->
            val innkommende = innkommendeOppgavetyper.oppgavetyper.find { it.id.equals(eksistereende.id) }
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