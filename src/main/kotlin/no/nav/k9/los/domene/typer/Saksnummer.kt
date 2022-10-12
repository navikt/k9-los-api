package no.nav.k9.los.domene.typer

import java.util.*
import java.util.regex.Pattern

class Saksnummer(saksnummer: String) {
    val verdi: String

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        } else if (other == null || javaClass != other.javaClass) {
            return false
        }
        val saksnummer = other as Saksnummer
        return verdi == saksnummer.verdi
    }

    override fun hashCode(): Int {
        return Objects.hash(verdi)
    }

    override fun toString(): String {
        return javaClass.simpleName + "<" + verdi + ">"
    }

    companion object {
        private const val CHARS = "a-z0-9_:-"
        private val VALID = Pattern.compile(
            "^(-?[1-9]|[a-z0])[$CHARS]*$",
            Pattern.CASE_INSENSITIVE
        )
        private val INVALID = Pattern.compile(
            "[^$CHARS]+",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
    }

    init {
        Objects.requireNonNull(saksnummer, "saksnummer")
        require(VALID.matcher(saksnummer).matches()) {
            // skal ikke skje, funksjonelle feilmeldinger håndteres ikke her.
            "Ugyldig saksnummer, støtter kun A-Z/0-9/:/-/_ tegn. Var: " + saksnummer.replace(
                INVALID.pattern().toRegex(),
                "?"
            ) + " (vasket)"
        }
        verdi = saksnummer
    }
}