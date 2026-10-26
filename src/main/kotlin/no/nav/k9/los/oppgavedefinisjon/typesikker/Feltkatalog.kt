package no.nav.k9.los.oppgavedefinisjon.typesikker

import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Datatype
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonerDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder.TransientFeltutleder
import no.nav.ung.kodeverk.api.Kodeverdi
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

abstract class Feltkatalog(val område: Områder) {
    protected abstract val grupper: List<Feltgruppe>

    val felter: List<Felt<*>> get() = grupper.flatMap { it.felter }

    fun tilDto(): FeltdefinisjonerDto {
        val duplikater = felter.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplikater.isEmpty()) { "Duplikate felt-id-er i $område: $duplikater" }
        return FeltdefinisjonerDto(område = område, feltdefinisjoner = felter.map { it.tilDto(område) }.toSet())
    }
}

abstract class Feltgruppe {
    val felter: List<Felt<*>>
        field = mutableListOf()

    private fun <F : Felt<*>> registrer(felt: F): F = felt.also { felter += it }

    protected fun string(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        beskrivelse: String? = null,
        kodeverk: KClass<*>? = null,
    ) = registrer(
        Enkeltfelt(id, visningsnavn, beskrivelse, Datatype.STRING, synlighet, kodeverk?.java?.simpleName, null, { it }, { it })
    )

    protected fun stringliste(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        beskrivelse: String? = null,
        kodeverk: KClass<*>? = null,
    ) = registrer(
        Listefelt(id, visningsnavn, beskrivelse, Datatype.STRING, synlighet, kodeverk?.java?.simpleName, { it }, { it })
    )

    protected fun boolean(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        beskrivelse: String? = null,
    ) = registrer(
        Enkeltfelt(id, visningsnavn, beskrivelse, Datatype.BOOLEAN, synlighet, null, null, Boolean::toString, String::toBooleanStrict)
    )

    protected fun timestamp(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        beskrivelse: String? = null,
    ) = registrer(
        Enkeltfelt(
            id, visningsnavn, beskrivelse, Datatype.TIMESTAMP, synlighet, null, null,
            { it.truncatedTo(ChronoUnit.MICROS).toString() }, LocalDateTime::parse,
        )
    )

    protected fun dato(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        beskrivelse: String? = null,
    ) = registrer(
        Enkeltfelt(id, visningsnavn, beskrivelse, Datatype.TIMESTAMP, synlighet, null, null, LocalDate::toString, LocalDate::parse)
    )

    protected fun duration(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        beskrivelse: String? = null,
        transientFeltutleder: KClass<out TransientFeltutleder>? = null,
    ) = registrer(
        Enkeltfelt(id, visningsnavn, beskrivelse, Datatype.DURATION, synlighet, null, transientFeltutleder, Duration::toString, Duration::parse)
    )

    protected fun <E> kode(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        kodeverk: KClass<E>,
        beskrivelse: String? = null,
    ): Enkeltfelt<E> where E : Enum<E>, E : Kodeverdi = registrer(
        Enkeltfelt(id, visningsnavn, beskrivelse, Datatype.STRING, synlighet, kodeverk.java.simpleName, null, { it.kode }, fraKode(kodeverk))
    )

    protected fun <E> kodeliste(
        id: String,
        visningsnavn: String,
        synlighet: Synlighet,
        kodeverk: KClass<E>,
        beskrivelse: String? = null,
    ): Listefelt<E> where E : Enum<E>, E : Kodeverdi = registrer(
        Listefelt(id, visningsnavn, beskrivelse, Datatype.STRING, synlighet, kodeverk.java.simpleName, { it.kode }, fraKode(kodeverk))
    )

    private fun <E> fraKode(kodeverk: KClass<E>): (String) -> E where E : Enum<E>, E : Kodeverdi = { kode ->
        kodeverk.java.enumConstants.firstOrNull { it.kode == kode }
            ?: throw IllegalArgumentException("Ukjent ${kodeverk.java.simpleName}: $kode")
    }
}

