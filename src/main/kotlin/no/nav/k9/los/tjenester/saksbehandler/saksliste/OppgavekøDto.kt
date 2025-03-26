package no.nav.k9.los.tjenester.saksbehandler.saksliste

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import java.time.LocalDate
import java.util.*

class OppgavekøDto(
    val id: UUID,
    var navn: String,
    var sortering: SorteringDto,
    var behandlingTyper: MutableList<BehandlingType>,
    var fagsakYtelseTyper: MutableList<FagsakYtelseType>,
    var andreKriterier: MutableList<AndreKriterierDto>,
    var skjermet: Boolean,
    var sistEndret: LocalDate,
    var antallBehandlinger: Int,
    var antallUreserverteOppgaver: Int,
    var saksbehandlere: MutableList<Saksbehandler>,
    var kriterier: List<KriteriumDto>
)
