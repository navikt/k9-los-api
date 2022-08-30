package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjoner

class Oppgavetyper(
    val område: String,
    val definisjonskilde: String,
    val oppgavetyper: Set<Oppgavetype>
) {

    constructor(dto: OppgavetyperDto, feltdefinisjoner: Feltdefinisjoner): this(
        område = dto.område,
        definisjonskilde = dto.definisjonskilde,
        oppgavetyper = dto.oppgavetyper.map {
            Oppgavetype(it, feltdefinisjoner)
        }.toSet()
    )


    fun finnForskjell(innkommendeOppgavetyper: Oppgavetyper): Triple<Oppgavetyper, Oppgavetyper, Oppgavetyper> {
        if (!innkommendeOppgavetyper.område.equals(this.område)) {
            throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av områder")
        }
        if (!innkommendeOppgavetyper.definisjonskilde.equals(this.definisjonskilde)) {
            throw IllegalStateException("Kan ikke sammenligne oppgavetyper på tvers av definisjonskilder")
        }
        val slettListe = mutableSetOf<Oppgavetype>()
        val leggTilListe = mutableSetOf<Oppgavetype>()
        val finnFeltforskjellListe = mutableSetOf<Oppgavetype>()

        innkommendeOppgavetyper.oppgavetyper.forEach { innkommende ->
            val eksisterende = oppgavetyper.find { it.id.equals(innkommende.id) }
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
                definisjonskilde = this.definisjonskilde,
                oppgavetyper = slettListe.toSet()
            ),
            Oppgavetyper(
                område = this.område,
                definisjonskilde = this.definisjonskilde,
                oppgavetyper = leggTilListe.toSet()
            ),
            Oppgavetyper(
                område = this.område,
                definisjonskilde = this.definisjonskilde,
                oppgavetyper = finnFeltforskjellListe.toSet()
            )
        )
    }
}