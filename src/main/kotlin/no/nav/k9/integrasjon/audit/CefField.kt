package no.nav.k9.integrasjon.audit

class CefField {
    private var key: CefFieldName
    var value: String?
        private set

    constructor(key: CefFieldName, value: String?) {
        this.key = key
        this.value = value
    }

    constructor(key: CefFieldName, value: Long) {
        this.key = key
        this.value = java.lang.Long.toString(value)
    }

    fun getKey(): CefFieldName {
        return key
    }

    /**
     * Nøkkel og verdi i "Commen Event Format (CEF)".
     */
    override fun toString(): String {
        return if (value == null) {
            ""
        } else key.kode + "=" + cefValueEscape(value!!)
    }

    companion object {
        private fun cefValueEscape(s: String): String {
            return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("=", "\\=")
        }
    }
}