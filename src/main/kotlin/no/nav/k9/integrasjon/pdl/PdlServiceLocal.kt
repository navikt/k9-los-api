package no.nav.k9.integrasjon.pdl

import io.ktor.util.KtorExperimentalAPI

class PdlServiceLocal @KtorExperimentalAPI constructor(
) : IPdlService {

    @KtorExperimentalAPI
    override suspend fun person(aktorId: String): PersonPdlResponse {
        return PersonPdlResponse(false, PersonPdl(
            data = PersonPdl.Data(
                hentPerson = PersonPdl.Data.HentPerson(
                    listOf(
                        element =
                        PersonPdl.Data.HentPerson.Folkeregisteridentifikator("012345678901")
                    ),
                    navn = listOf(
                        PersonPdl.Data.HentPerson.Navn(
                            etternavn = "Etternavn",
                            forkortetNavn = "ForkortetNavn",
                            fornavn = "Fornavn",
                            mellomnavn = null
                        )
                    ),
                    kjoenn = listOf(
                        PersonPdl.Data.HentPerson.Kjoenn(
                            "KVINNE"
                        )
                    ),
                    doedsfall = emptyList()
                )
            )
        ))
    }

    @KtorExperimentalAPI
    override suspend fun identifikator(fnummer: String): PdlResponse {
        return PdlResponse(false, AktøridPdl(
            data = AktøridPdl.Data(
                hentIdenter = AktøridPdl.Data.HentIdenter(
                    identer = listOf(
                        AktøridPdl.Data.HentIdenter.Identer(
                            gruppe = "AKTORID",
                            historisk = false,
                            ident = "2392173967319"
                        )
                    )
                )
            )
        ))
    }
}



