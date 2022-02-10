package no.nav.k9.fagsystem.k9sak

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon

interface OppgaveType {
    val kode: String
}

enum class K9SakOppgaveType(override val kode: String) : OppgaveType {
    BESLUTTER(AksjonspunktDefinisjon.FATTER_VEDTAK.kode),

}