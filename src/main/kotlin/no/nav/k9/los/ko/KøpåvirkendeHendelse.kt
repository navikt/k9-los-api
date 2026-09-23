package no.nav.k9.los.ko

import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.db.EksternOppgaveId

interface KøpåvirkendeHendelse
data class Kødefinisjon(val køId : Long) : KøpåvirkendeHendelse
data class KødefinisjonSlettet(val køId : Long) : KøpåvirkendeHendelse

data class OppgaveHendelseMottatt (val fagsystem: Fagsystem, val eksternId : EksternOppgaveId) : KøpåvirkendeHendelse

sealed interface ReservasjonHendelse : KøpåvirkendeHendelse {
    val område: Områder
    val reservasjonsnøkkel: String
}
data class ReservasjonAnnullert (override val område: Områder, override val reservasjonsnøkkel : String) : ReservasjonHendelse
data class ReservasjonEndret (override val område: Områder, override val reservasjonsnøkkel : String) : ReservasjonHendelse
data class ReservasjonTatt (override val område: Områder, override val reservasjonsnøkkel : String) : ReservasjonHendelse

//kunne også hatt egen hendelse for når det plukkes fra front av kø for å prioritere refresh av flere oppgaver i køer som brukes mest aktivt. Plukking fra kø vil uansett trigge ReservasjonTatt
