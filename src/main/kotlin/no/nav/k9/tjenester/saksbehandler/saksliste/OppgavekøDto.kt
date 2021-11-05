package no.nav.k9.tjenester.saksbehandler.saksliste

import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.Saksbehandler
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
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
        var saksbehandlere: MutableList<Saksbehandler>
)
