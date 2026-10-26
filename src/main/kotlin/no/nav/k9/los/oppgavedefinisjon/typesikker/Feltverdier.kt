package no.nav.k9.los.oppgavedefinisjon.typesikker

import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgaveuthenting.Oppgave

class Feltverdier(private val oppgavetype: Oppgavetypedefinisjon) {
    private val verdier = linkedMapOf<Felt<*>, List<String?>>()

    operator fun <T : Any> set(felt: Enkeltfelt<T>, verdi: T?) {
        sett(felt, listOf(verdi?.let(felt.serialiser)))
    }

    operator fun <T : Any> set(felt: Listefelt<T>, verdier: List<T>) {
        sett(felt, verdier.map(felt.serialiser).ifEmpty { listOf(null) })
    }

    private fun sett(felt: Felt<*>, serialisert: List<String?>) {
        require(oppgavetype.inneholder(felt)) { "Feltet ${felt.id} er ikke definert for oppgavetype ${oppgavetype.id}" }
        verdier[felt] = serialisert
    }

    fun tilDto(): List<OppgaveFeltverdiDto> {
        val manglende = oppgavetype.felter
            .filter { it.påkrevd && !it.felt.listetype && it.felt.transientFeltutleder == null }
            .map { it.felt }
            .filterNot { it in verdier }
        require(manglende.isEmpty()) { "Påkrevde felt er ikke satt for oppgavetype ${oppgavetype.id}: $manglende" }

        return verdier.flatMap { (felt, liste) -> liste.map { OppgaveFeltverdiDto(nøkkel = felt.id, verdi = it) } }
    }
}

fun <T : Any> OppgaveV3.hent(felt: Enkeltfelt<T>): T? = hentVerdi(felt.id)?.let(felt.deserialiser)

fun <T : Any> Oppgave.hent(felt: Enkeltfelt<T>): T? = hentVerdi(felt.id)?.let(felt.deserialiser)

