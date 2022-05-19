package no.nav.k9.tjenester.kodeverk

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import no.nav.k9.domene.lager.oppgave.Kodeverdi
import no.nav.k9.domene.modell.AndreKriterierType
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakStatus
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.KøKriterierType
import no.nav.k9.domene.modell.KøSortering
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.Venteårsak
import kotlin.reflect.full.memberProperties

class HentKodeverkTjeneste() {


    companion object {
        private val KODEVERKLISTE = makeMap()

        private fun makeMap(): MutableMap<String, Collection<out Kodeverdi>> {
            val koder = mutableMapOf<String, Collection<out Kodeverdi>>()

            koder[BehandlingType::class.java.simpleName] = BehandlingType.values().asList()
            koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.values().asList()
            koder[KøSortering::class.java.simpleName] = KøSortering.values().asList()
            koder[FagsakStatus::class.java.simpleName] = FagsakStatus.values().asList()
            koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.values().asList()
            koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.values().asList()
            koder[Venteårsak::class.java.simpleName] = Venteårsak.values().asList()
            koder[KøKriterierType::class.java.simpleName] = KøKriterierType.values().asList()
            return koder
        }
    }

    fun hentGruppertKodeliste(): MutableMap<String, Collection<Kodeverdi>> {
        return KODEVERK_ENUM
    }

    fun hentGruppertKodeliste2() = FullKodeverkMap(KODEVERKLISTE)

    private var KODEVERK_ENUM = makeMap()

    private fun makeMap(): MutableMap<String, Collection<Kodeverdi>> {
        val koder = mutableMapOf<String, Collection<Kodeverdi>>()

        koder[BehandlingType::class.java.simpleName] = BehandlingType.values().asList()
        koder[FagsakYtelseType::class.java.simpleName] = FagsakYtelseType.values().asList()
        koder[KøSortering::class.java.simpleName] = KøSortering.values().asList()
        koder[FagsakStatus::class.java.simpleName] = FagsakStatus.values().asList()
        koder[AndreKriterierType::class.java.simpleName] = AndreKriterierType.values().asList()
        koder[BehandlingStatus::class.java.simpleName] = BehandlingStatus.values().asList()
        koder[Venteårsak::class.java.simpleName] = Venteårsak.values().asList()
        koder[KøKriterierType::class.java.simpleName] = KøKriterierType.values().asList()
        return koder
    }
}

//fun main() {
////    val module = SimpleModule()
////    module.addSerializer(FullKodeverdiSerializer())
//    val o = objectMapper()
////    o.registerModule(module)
//    println(o.writeValueAsString(FullKodeverkMap(mapOf(
//        KøKriterierType::class.simpleName!! to KøKriterierType.values().asList(),
//        BehandlingType::class.simpleName!! to BehandlingType.values().asList(),
//    ))))
//}

@JsonSerialize(using = FullKodeverdiMapSerializer::class)
class FullKodeverkMap(val kodeMap: Map<String, Collection<Kodeverdi>>)


class FullKodeverdiMapSerializer : StdSerializer<FullKodeverkMap>(FullKodeverkMap::class.java) {
    override fun serialize(value: FullKodeverkMap, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        value.kodeMap.forEach {
            gen.writeArrayFieldStart(it.key)
            it.value.forEach { kodeverdi -> serialize(kodeverdi, gen) }
            gen.writeEndArray()
        }
        gen.writeEndObject()

    }

    private fun serialize(value: Kodeverdi, gen: JsonGenerator) {
        val enumFields = Enum::class.java.declaredFields.map { it.name }
        val ignoredProps = value::class.java.declaredFields
            .filter {it.getAnnotation(JsonIgnore::class.java) !=null }
            .map { it.name }

        val kodeProps = value::class.memberProperties
            .filter { !ignoredProps.contains(it.name) }
            .filter { !enumFields.contains(it.name) }

        gen.writeStartObject()
        kodeProps.forEach {
            gen.writeObjectField(it.name, it.getter.call(value))
        }
        gen.writeEndObject()

    }

}
