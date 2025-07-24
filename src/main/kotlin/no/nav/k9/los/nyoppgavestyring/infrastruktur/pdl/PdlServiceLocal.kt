package no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl


class PdlServiceLocal : IPdlService {

    override suspend fun person(aktorId: String): PersonPdlResponse {
        return PersonPdlResponse(false, PersonPdl(
            data = PersonPdl.Data(
                hentPerson = PersonPdl.Data.HentPerson(
                    listOf(
                        element =
                        PersonPdl.Data.HentPerson.Folkeregisteridentifikator("01234567890")
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
        )
        )
    }

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
        )
        )
    }
}



