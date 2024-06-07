package no.nav.k9.los.nyoppgavestyring.mottak.oppgave.test

import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import java.util.*

class YtelsetestBuilder() {

    fun opprettEventserie(): MutableList<BehandlingProsessEventDto> {
        val eventer = mutableListOf<BehandlingProsessEventDto>()
        val builder = BehandlingProsessEventDtoBuilder(
            ytelseType = FagsakYtelseType.entries[Random().nextInt(8)],
            behandlingTypeKode = BehandlingType.entries[Random().nextInt(2)]
        )
        val sb = Saksbehandler(
            id = null,
            brukerIdent = "Z123456",
            navn = "Saksbehandler Sara",
            epost = "saksbehandler@nav.no",
            reservasjoner = mutableSetOf(),
            enhet = "NAV DRIFT"
        )
        val vektingAntallEventer = Random().nextInt(70)

        eventer.add(builder.opprettet().build())
        if (vektingAntallEventer <= 1) return eventer

        eventer.add(builder.vurderSykdom().build())
        if (vektingAntallEventer <= 2) return eventer

        eventer.add(builder.foreslåVedtak().build())
        if (vektingAntallEventer <= 3) return eventer

        eventer.add(builder.venterPåInntektsmelding().build())
        if (vektingAntallEventer <= 6) return eventer

        eventer.add(builder.foreslåVedtak().build())
        eventer.add(builder.hosBeslutter(sb).build())
        eventer.add(builder.avsluttet(sb).build())

        return eventer
    }
}