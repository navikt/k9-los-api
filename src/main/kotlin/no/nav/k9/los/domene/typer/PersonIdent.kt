package no.nav.k9.los.domene.typer

data class PersonIdent(private val ident: String){
    private val CHECKSUM_EN_VECTOR = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
    private val CHECKSUM_TO_VECTOR = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

    private val FNR_LENGDE = 11

    private val PERSONNR_LENGDE = 5

    private fun validerFnrStruktur(foedselsnummer: String): Boolean {
        if (foedselsnummer.length != FNR_LENGDE) {
            return false
        }
        var checksumEn: Int = FNR_LENGDE - sum(
            foedselsnummer,
            *CHECKSUM_EN_VECTOR
        ) % FNR_LENGDE
        if (checksumEn == FNR_LENGDE) {
            checksumEn = 0
        }
        var checksumTo: Int = FNR_LENGDE - sum(
            foedselsnummer,
            *CHECKSUM_TO_VECTOR
        ) % FNR_LENGDE
        if (checksumTo == FNR_LENGDE) {
            checksumTo = 0
        }
        return (checksumEn == Character.digit(foedselsnummer[FNR_LENGDE - 2], 10)
                && checksumTo == Character.digit(foedselsnummer[FNR_LENGDE - 1], 10))
    }

    private fun sum(foedselsnummer: String, vararg faktors: Int): Int {
        var sum = 0
        var i = 0
        val l = faktors.size
        while (i < l) {
            sum += Character.digit(foedselsnummer[i], 10) * faktors[i]
            ++i
        }
        return sum
    }

    private fun isFdatNummer(personnummer: String?): Boolean {
        return personnummer != null && personnummer.length == PERSONNR_LENGDE && personnummer.startsWith(
            "0000"
        )
    }

    private fun getPersonnummer(str: String?): String? {
        return if (str == null || str.length < PERSONNR_LENGDE) null else str.substring(
            str.length - PERSONNR_LENGDE,
            str.length
        )
    }

    fun erGyldigFnr(str: String?): Boolean {
        if (str == null) {
            return false
        }
        val s = str.trim { it <= ' ' }
        return s.length == FNR_LENGDE && !isFdatNummer(getPersonnummer(s)) && validerFnrStruktur(
            s
        )
    }
}