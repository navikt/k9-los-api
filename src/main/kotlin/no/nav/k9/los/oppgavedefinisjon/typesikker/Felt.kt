package no.nav.k9.los.oppgavedefinisjon.typesikker

import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Datatype
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.KodeverkReferanseDto
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder.TransientFeltutleder
import kotlin.reflect.KClass

sealed class Felt<T : Any>(
    val id: String,
    val visningsnavn: String,
    val beskrivelse: String?,
    val datatype: Datatype,
    val synlighet: Synlighet,
    val kodeverk: String?,
    val transientFeltutleder: KClass<out TransientFeltutleder>?,
    val serialiser: (T) -> String,
    val deserialiser: (String) -> T,
) {
    abstract val listetype: Boolean

    fun tilDto(område: Områder) = FeltdefinisjonDto(
        id = id,
        visningsnavn = visningsnavn,
        beskrivelse = beskrivelse,
        listetype = listetype,
        tolkesSom = datatype,
        synlighet = synlighet,
        kodeverkreferanse = kodeverk?.let { KodeverkReferanseDto(område = område, eksternId = it) },
        transientFeltutleder = transientFeltutleder?.java?.canonicalName,
    )

    override fun toString() = id
}

class Enkeltfelt<T : Any> internal constructor(
    id: String,
    visningsnavn: String,
    beskrivelse: String?,
    datatype: Datatype,
    synlighet: Synlighet,
    kodeverk: String?,
    transientFeltutleder: KClass<out TransientFeltutleder>?,
    serialiser: (T) -> String,
    deserialiser: (String) -> T,
) : Felt<T>(id, visningsnavn, beskrivelse, datatype, synlighet, kodeverk, transientFeltutleder, serialiser, deserialiser) {
    override val listetype = false
}

class Listefelt<T : Any> internal constructor(
    id: String,
    visningsnavn: String,
    beskrivelse: String?,
    datatype: Datatype,
    synlighet: Synlighet,
    kodeverk: String?,
    serialiser: (T) -> String,
    deserialiser: (String) -> T,
) : Felt<T>(id, visningsnavn, beskrivelse, datatype, synlighet, kodeverk, null, serialiser, deserialiser) {
    override val listetype = true
}

