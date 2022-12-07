package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.DataeksportApis() {
    val nøkkeltallTjeneste by inject<NokkeltallTjeneste>()

    route("eksport") {
        get("/loeste-aksjonspunkter") {
            val antallDager = call.request.queryParameters["fraDato"]?.toInt() ?: 55

            val filtre = try {
                hentFiltre(call.request.queryParameters)
            } catch (e: UnsupportedOperationException) {
                call.respondText(text = e.message!!, status = HttpStatusCode.NotImplemented)
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName, "løste_aksjonspunkter.csv"
                ).toString()
            )
            val historikk = nøkkeltallTjeneste.hentFerdigstilteOppgaverHistorikk(
                *filtre.toTypedArray(),
                antallDagerHistorikk = antallDager
            )
            call.respondText(historikk.tilCsv())
        }
    }
}

fun HistorikkElementAntall.tilCsvVerdi(): String {
    return (historikkElement.values + "$antall").joinToString(",")
}

fun HistorikkElementAntall.tilCsvHeader(): String {
    return (historikkElement.keys + "antall").joinToString(",")
}

fun Collection<HistorikkElementAntall>.tilCsv(): String {
    val rader = listOf(first().tilCsvHeader()) + map { it.tilCsvVerdi() }
    return rader.joinToString("\n")
}

fun hentFiltre(queryParameters: Parameters): List<VelgbartHistorikkfelt> {
    val filtre = queryParameters["filtre"]?.split(",") ?: emptySet()

    val muligeValgbareFilterkoder = VelgbartHistorikkfelt.values()
        .filterNot { it == VelgbartHistorikkfelt.SAKSBEHANDLER }
        .map { it.toString() }

    val filtreIkkeGjenkjent = filtre.filterNot { filter -> muligeValgbareFilterkoder.contains(filter) }
    if (filtreIkkeGjenkjent.isNotEmpty()) {
        throw UnsupportedOperationException("Oppgitt filter '$filtreIkkeGjenkjent', er ikke blant mulige valg $muligeValgbareFilterkoder")
    }

    return filtre.map { VelgbartHistorikkfelt.valueOf(it) }.ifEmpty { listOf(VelgbartHistorikkfelt.DATO) }
}