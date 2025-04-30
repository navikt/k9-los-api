package no.nav.k9.los.domene.modell

import no.nav.k9.los.domene.modell.KriteriumDto

fun interface KøKriterierTypeValidator {
    fun valider(kriteriumDto: KriteriumDto)
}

object KøKritererTypeValidatorer {
    private val RangeValidator = KøKriterierTypeValidator {
        if (it.fom.isNullOrEmpty() && it.tom.isNullOrEmpty())
            throw IllegalArgumentException("fom og tom kan ikke være tomme for ${it.kriterierType}, men var fom=$it.fom" + " tom=${it.tom})")
    }

    val HeltallRangeValidator = KøKriterierTypeValidator {
        RangeValidator.valider(it)
        if (it.fom != null && it.fom.toIntOrNull() == null) throw IllegalArgumentException("fra og med må være heltall men var ${it.fom}")
        if (it.tom != null && it.tom.toIntOrNull() == null) throw IllegalArgumentException("til og med må være heltall men var ${it.tom}")
    }

    val FlaggValidator = KøKriterierTypeValidator {
        require(it.checked != null) { "checked må være satt ved flagg felter" }
        if (it.checked) {
            require(it.inkluder != null) { "hvis checked er satt så må inkluder ha verdi" }
        }
    }

    val KodeverkValidator: ((String) -> Unit) -> KøKriterierTypeValidator = { kodeVerdiValidator ->
        KøKriterierTypeValidator { kriteriumDto ->
            if (kriteriumDto.koder == null) throw IllegalArgumentException("koder må være satt men var null")
            kriteriumDto.koder.forEach { k ->
                try { kodeVerdiValidator.invoke(k) }
                catch (e : Exception) {
                    throw IllegalArgumentException("Feil ved parsing av koder for " +
                            "kriterierType=${kriteriumDto.kriterierType.kode} og " +
                            "kodeverk=${kriteriumDto.kriterierType.felttypeKodeverk}", e)
                }
            }
        }
    }
}