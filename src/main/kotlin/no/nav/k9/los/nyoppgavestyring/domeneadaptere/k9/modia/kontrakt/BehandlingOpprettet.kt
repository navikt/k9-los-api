package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.kontrakt

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import java.time.LocalDateTime


data class BehandlingOpprettet(
    val aktoerREF: List<AktoerREF>,
    /*val identREF: List<IdentREF>,
    SOB: Vi har lagt til muligheten for å sende med ident i tillegg til aktør,
    så om dere har ident tilgjengelig i det dere generer meldingen hadde
    vi satt stor pris på om dere sendte det med.
     */
    val ansvarligEnhetREF: String,
    val applikasjonBehandlingREF: String,
    val applikasjonSakREF: String,
    val behandlingsID: String,
    val behandlingstema: Behandlingstema,
    val behandlingstype: Behandlingstype,
    val hendelseType: String,
    val hendelsesId: String,
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val hendelsesTidspunkt: LocalDateTime,
    val hendelsesprodusentREF: HendelsesprodusentREF,
    val primaerBehandlingREF: PrimaerBehandlingREF?,
    val sakstema: Sakstema,
    val sekundaerBehandlingREF: List<SekundaerBehandlingREF>,
    val styringsinformasjonListe: List<StyringsinformasjonListe>
) {
    data class AktoerREF(
        val aktoerId: String
    )

    data class Behandlingstema(
        val kodeRef: String,
        val kodeverksRef: String,
        val value: String
    )

    data class Behandlingstype(
        val kodeRef: String,
        val kodeverksRef: String,
        val value: String
    )

    data class HendelsesprodusentREF(
        val kodeRef: String,
        val kodeverksRef: String,
        val value: String
    )

    data class PrimaerBehandlingREF(
        val behandlingsREF: String,
        val type: Type
    ) {
        data class Type(
            val kodeRef: String,
            val kodeverksRef: String,
            val value: String
        )
    }

    data class Sakstema(
        val kodeRef: String,
        val kodeverksRef: String,
        val value: String
    )

    data class SekundaerBehandlingREF(
        val behandlingsREF: String,
        val type: Type
    ) {
        data class Type(
            val kodeRef: String,
            val kodeverksRef: String,
            val value: String
        )
    }

    data class StyringsinformasjonListe(
        val key: String,
        val type: String,
        val value: String
    )
}