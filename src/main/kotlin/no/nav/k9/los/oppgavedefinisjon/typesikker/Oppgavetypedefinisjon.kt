package no.nav.k9.los.oppgavedefinisjon.typesikker

import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavefeltDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeDto
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetyperDto

class Oppgavefelt(val felt: Felt<*>, val visPåOppgave: Boolean, val påkrevd: Boolean)

class Oppgavefeltbygger internal constructor() {
    internal val felter = linkedMapOf<String, Oppgavefelt>()

    fun påkrevd(felt: Felt<*>, visPåOppgave: Boolean = true) = leggTil(Oppgavefelt(felt, visPåOppgave, påkrevd = true))

    fun valgfri(felt: Felt<*>, visPåOppgave: Boolean = true) = leggTil(Oppgavefelt(felt, visPåOppgave, påkrevd = false))

    fun inkluder(sett: Oppgavefeltsett) = sett.felter.forEach(::leggTil)

    private fun leggTil(oppgavefelt: Oppgavefelt) {
        require(oppgavefelt.felt.id !in felter) { "Feltet ${oppgavefelt.felt.id} er lagt til flere ganger" }
        felter[oppgavefelt.felt.id] = oppgavefelt
    }
}

class Oppgavefeltsett(bygg: Oppgavefeltbygger.() -> Unit) {
    val felter: List<Oppgavefelt> = Oppgavefeltbygger().apply(bygg).felter.values.toList()
}

class Oppgavetypedefinisjon(
    val id: String,
    private val behandlingsUrl: (frontendUrl: String) -> String,
    bygg: Oppgavefeltbygger.() -> Unit,
) {
    private val oppgavefelter: Map<String, Oppgavefelt> = Oppgavefeltbygger().apply(bygg).felter

    val felter: Collection<Oppgavefelt> get() = oppgavefelter.values

    fun inneholder(felt: Felt<*>) = oppgavefelter[felt.id]?.felt === felt

    fun tilDto(frontendUrl: String) = OppgavetypeDto(
        id = id,
        oppgavebehandlingsUrlTemplate = behandlingsUrl(frontendUrl),
        oppgavefelter = felter.map { OppgavefeltDto(it.felt.id, visPåOppgave = it.visPåOppgave, påkrevd = it.påkrevd) }.toSet(),
    )
}

abstract class Oppgavetypekatalog(private val feltkatalog: Feltkatalog) {
    abstract val oppgavetyper: List<Oppgavetypedefinisjon>

    fun hent(id: String): Oppgavetypedefinisjon =
        oppgavetyper.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Oppgavetype $id er ikke definert for ${feltkatalog.område}")

    fun tilDto(frontendUrl: String): OppgavetyperDto {
        val kjenteFelter = feltkatalog.felter.toSet()
        oppgavetyper.forEach { oppgavetype ->
            val ukjente = oppgavetype.felter.map { it.felt }.filterNot { it in kjenteFelter }
            require(ukjente.isEmpty()) {
                "Oppgavetype ${oppgavetype.id} bruker felt som ikke er registrert i feltkatalogen for ${feltkatalog.område}: $ukjente"
            }
        }
        return OppgavetyperDto(
            område = feltkatalog.område,
            oppgavetyper = oppgavetyper.map { it.tilDto(frontendUrl) }.toSet(),
        )
    }
}

