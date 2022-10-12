package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

object EnheterSomSkalUtelatesFraLos {
    val utelatteEnhetider = setOf("2103")

    fun sjekkKanBrukes(enhet: String?): Boolean {
        if (enhet.isNullOrBlank()) return true
        return utelatteEnhetider.none { utelattEnhetId -> enhet.contains(utelattEnhetId) }
    }
}