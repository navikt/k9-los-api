package no.nav.k9.los.nyoppgavestyring.ko.refresh.k9sak

import no.nav.k9.los.domene.modell.Fagsystem

interface KøpåvirkendeHendelse
data class PlukketFraKø(val køId: Long) : KøpåvirkendeHendelse
data class OppgaveHendelseMottatt (val fagsystem: Fagsystem) : KøpåvirkendeHendelse



