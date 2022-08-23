package no.nav.k9.domene.lager.oppgave.v3

data class Datatype(
    val id: Long?,
    val eksternId: String,
    val eier: Long, //område.id
    val listeType: Boolean,
    val implementasjonstype: String,
    val visSomMuligFilter: Boolean
)
