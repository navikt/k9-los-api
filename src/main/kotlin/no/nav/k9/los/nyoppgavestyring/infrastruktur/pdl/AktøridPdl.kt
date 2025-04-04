package no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl


data class AktøridPdl(
    val `data`: Data
) {
    data class Data(
        var hentIdenter: HentIdenter?
    ) {
        data class HentIdenter(
            val identer: List<Identer>
        ) {
            data class Identer(
                val gruppe: String,
                val historisk: Boolean,
                var ident: String
            )
        }
    }
}