package no.nav.k9.los.integrasjon.dto.kodeverk

data class KodeDto(
    var kodeverk: String,
    var kode: String,
    private var navn: String
) {

    override fun toString(): String {
        return "Kode{" +
                "kodeverk='" + kodeverk + '\'' +
                ", kode='" + kode + '\'' +
                ", navn='" + navn + '\'' +
                '}'
    }
}