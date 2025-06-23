package no.nav.k9.los.utils

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.TransientFeilHåndterer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InterruptedIOException
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TransientFeilHåndtererTest {
    @Test
    fun operasjon_som_ikke_feiler_skal_kalles_nøyaktig_en_gang() = runTest {
        var teller = 0;
        TransientFeilHåndterer().utfør("test") { teller++ }
        assertThat(teller).isEqualTo(1);
    }

    @Test
    fun operasjon_som_feiler_med_transient_feil_skal_kalles_på_nytt() = runTest{
        var teller = 0;
        TransientFeilHåndterer().utfør("test") {
            teller++;
            if (teller < 2) {
                throw InterruptedIOException()
            }
        }
        assertThat(teller).isEqualTo(2);
    }

    @Test
    fun operasjon_som_feiler_med_transient_feil_kontinuerlig_skal_til_slutt_kaste_feilen_videre() = runTest {
        var teller = 0;
        assertThrows<RuntimeException>("Operasjonen test feilet. Har forsøkt 4 ganger og ventet totalt 7ms") {
            TransientFeilHåndterer(pauseInitiell = 1.toDuration(DurationUnit.MILLISECONDS), giOppEtter = 10.toDuration(DurationUnit.MILLISECONDS)).utfør("test") {
                teller++;
                throw InterruptedIOException()
            }
        }
        //kaller etter 0ms, gjør retry etter 1, 2, 4, 8 millisekunder, så totalt skal det kalles 5 ganger
        assertThat(teller).isEqualTo(5);
    }

    @Test
    fun operasjon_som_feiler_med_ikke_transient_feil_skal_kaste_feilen_videre() = runTest {
        assertThrows<IllegalArgumentException> { TransientFeilHåndterer().utfør("test") { throw IllegalArgumentException() } }
    }
}