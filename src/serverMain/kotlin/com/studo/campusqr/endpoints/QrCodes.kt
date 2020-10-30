package com.studo.campusqr.endpoints

import com.studo.campusqr.common.ClientLocation
import com.studo.campusqr.common.utils.LocalizedString
import com.studo.campusqr.database.MainDatabase.getConfig
import com.studo.campusqr.database.getConfigs
import com.studo.campusqr.extensions.get
import com.studo.campusqr.extensions.language
import com.studo.campusqr.extensions.respondForbidden
import com.studo.campusqr.utils.AuthenticatedApplicationCall
import io.ktor.html.*
import io.ktor.http.*
import kotlinx.html.*

suspend fun AuthenticatedApplicationCall.viewSingleQrCode() {
  if (!sessionToken.isAuthenticated) {
    respondForbidden()
    return
  }

  val location = parameters["id"]?.let { getLocation(it) }?.toClientClass(language)
  val configs = getConfigs(language)

  respondHtml(HttpStatusCode.OK) {
    lang = language
    headTemplate("View Code", js = "viewQR/qrcode.min.js", css = "viewQR/styles.css", async = false) {
      meta(name = "qrCodeBaseUrl", content = configs.getValue("qrCodeBaseUrl"))
    }

    body {
      if (location == null) {
        p {
          +LocalizedString(
            "This location does not exist. Please go back and try again.",
            "Diese Location existiert nicht, bitte gehe zurück und versuche es erneut."
          ).get(this@viewSingleQrCode)
        }
      } else {
        renderPrintPage(language, smallPages = location.seatCount != null) {
          renderLocation(location, configs)
        }
      }
    }
  }
}

suspend fun AuthenticatedApplicationCall.viewCheckoutCode() {
  if (!sessionToken.isAuthenticated) {
    respondForbidden()
    return
  }
  val configs = getConfigs(language)

  respondHtml(HttpStatusCode.OK) {
    lang = language
    headTemplate("View Code", js = "viewQR/qrcode.min.js", css = "viewQR/styles.css", async = false) {
      meta(name = "qrCodeBaseUrl", content = configs.getValue("qrCodeBaseUrl"))
    }

    body {
      renderPrintPage(language, smallPages = false) {
        renderQrCodePage(
          "Check Out",
          "checkout",
          configs,
          subtext1 = configs.getValue("scanCheckoutSubtext1"),
          subtext2 = configs.getValue("scanCheckoutSubtext1"),
          subtitle = ""
        )
      }
    }
  }
}

suspend fun AuthenticatedApplicationCall.viewAllQrCodes() {
  if (!sessionToken.isAuthenticated) {
    respondForbidden()
    return
  }

  val locations = getAllLocations(language)
  val configs = getConfigs(language)

  respondHtml(HttpStatusCode.OK) {
    lang = language
    headTemplate("View Code", js = "viewQR/qrcode.min.js", css = "viewQR/styles.css", async = false) {
      meta(name = "qrCodeBaseUrl", content = configs.getValue("qrCodeBaseUrl"))
    }

    body {
      if (locations.isEmpty()) {
        p {
          +LocalizedString(
            "No locations have been added yet. Go back to create some.",
            "Es sind noch keine Orte eingetragen. Gehen Sie zurück um welche zu erstellen."
          ).get(this@viewAllQrCodes)
        }
      } else {
        renderPrintPage(language, smallPages = false) {
          for (location in locations) {
            renderLocation(location, configs)
          }
        }
      }
    }
  }
}

fun FlowContent.renderPrintPage(language: String, smallPages: Boolean, block: FlowContent.() -> Unit) {
  if (smallPages && getConfig("multiSeatLocationsUseSmallCheckinPages")) {
    style {
      +"@page { size: A4 landscape; }"
    }
  }
  noScript {
    +"You need to enable JavaScript to run this app."
  }
  header("noprint") {
    h2 {
      +"QR Codes"
    }
    p {
      +LocalizedString(
        "This website is suited for printing. " +
            "The QR codes will be put on separate pages and this hint will not be printed.",
        "Diese Website ist zum Drucken geeignet. " +
            "Die QR Codes werden auf verschiedene Seiten aufgeteilt und dieser Hinweis wird nicht mitgedruckt."
      ).get(language)
    }
    p {
      id = "loading-text"
      +LocalizedString("Loading... ", "Lädt... ").get(language)
    }
  }
  div("hidden") {
    id = "all-codes"
    block()
  }
  script {
    src = "/static/viewQR/generate.js"
  }
}

fun FlowContent.renderLocation(location: ClientLocation, configs: Map<String, String>) {
  val multiSeatLocationsUseSmallCheckinPages: Boolean = getConfig("multiSeatLocationsUseSmallCheckinPages")
  if (location.seatCount != null) {
    if (multiSeatLocationsUseSmallCheckinPages) {
      // Put two portrait A5 pages side by side on each landscape A4 sheet
      for (skippedSeat in 1..location.seatCount step 2) {
        div("small-pages-container") { // Crops the inner container
          div("small-pages-scaling-container") { // Scales content to 0.5
            // Render the two pages as if they were full-size
            for (seat in listOf(skippedSeat, skippedSeat + 1)) {
              val paddedSeat = seat.toString().padStart(location.seatCount.toString().length, '0')
              renderQrCodePage("${location.name} #$paddedSeat", "${location.id}-$seat", configs, smallPage = true)
            }
          }
        }
      }
    } else {
      for (seat in 1..location.seatCount) {
        val paddedSeat = seat.toString().padStart(location.seatCount.toString().length, '0')
        renderQrCodePage("${location.name} #$paddedSeat", "${location.id}-$seat", configs)
        div("break") {}
      }
    }
  } else {
    renderQrCodePage(location.name, location.id, configs)
  }
}

fun FlowContent.renderQrCodePage(
  name: String,
  id: String,
  configs: Map<String, String>,
  subtext1: String = configs.getValue("scanSubtext1"),
  subtext2: String = configs.getValue("scanSubtext2"),
  subtitle: String = "Check In",
  smallPage: Boolean = false, // True if there are two Din A5 pages side by side on one Din A4 sheet
) {
  div("page") {
    div("header") {
      h1 {
        +name
      }
      p {
        +subtitle
      }
    }
    div("qrcode") {
      this.id = id
    }
    div("footer") {
      p {
        +subtext1
      }
      p {
        +subtext2
      }
    }
  }
}