package com.studo.campusqr.endpoints

import com.studo.campusqr.common.utils.LocalizedString
import com.studo.campusqr.database.getConfigs
import com.studo.campusqr.extensions.get
import com.studo.campusqr.extensions.language
import com.studo.campusqr.extensions.runOnDb
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import kotlinx.html.*
import java.util.*


suspend fun ApplicationCall.userFrontend() {
  val fullLocationId = parameters["l"]
  var seat: Int? = null
  val locationId = if (fullLocationId?.contains("-") == true) {
    seat = fullLocationId.substringAfterLast("-", "").trimStart('0').toIntOrNull()
    fullLocationId.substringBeforeLast("-")
  } else {
    fullLocationId
  }
  val location = locationId?.let { id -> getLocationOrNull(id) }

  val configs = getConfigs(language)
  val showVerificationAnimation = runOnDb { getConfig<Int>("showVerificationAnimation") } == 1

  val now = Date()

  respondHtml(HttpStatusCode.OK) {
    lang = language
    headTemplate("Check In", css = "userFrontend/userFrontend.css", js = "userFrontend/userFrontend.js", block = {
      if (location != null) {
        meta("location-name", location.name) {}
      }
    })
    body {
      noScript {
        +"You need to enable JavaScript to run this app."
      }

      if (location == null) {
        // Location not found
        div("overlay") {
          id = "overlay"
          img {
            src = "/static/userFrontend/reload.svg"
          }
          span {
            id = "overlay-retry"
            +LocalizedString(
              "Please close this page and scan the QR code again.",
              "Bitte schließen Sie diese Seite und scannen Sie den QR Code erneut."
            ).get(this@userFrontend)
          }
        }
      } else {
        val locationNameWithSeat = (
            location.name + if (location.seatCount != null && seat != null) {
              " #${seat.toString().padStart(location.seatCount.toString().length, '0')}"
            } else "")

        div("overlay hidden") {
          id = "overlay"
          img {
            src = "/static/userFrontend/reload.svg"
          }
          span {
            id = "overlay-retry"
            +LocalizedString(
              "Please close this page and scan the QR code again.",
              "Bitte schließen Sie diese Seite und scannen Sie den QR Code erneut."
            ).get(this@userFrontend)
          }
          span("hidden") {
            id = "overlay-expired"
            +LocalizedString(
              "Your check-in has expired. Please close this page and scan the QR code again.",
              "Ihr Check-in ist abgelaufen. Bitte schließen Sie diese Seite und scannen Sie den QR Code erneut."
            ).get(this@userFrontend)
          }
        }
        div {
          id = "all-content-wrapper"
          div("header") {
            img {
              src = configs.getValue("logoUrl")
              alt = "Logo"
            }
            h2 {
              +"Campus Check-in"
            }
          }
          div("content") {
            div {
              id = "form"
              div("location-wrapper") {
                div("icon wrap") {
                  img {
                    src = "/static/userFrontend/locationIcon.svg"
                  }
                }
                span("location") {
                  +LocalizedString(
                    "Location: ",
                    "Ort: "
                  ).get(this@userFrontend)
                  b { +locationNameWithSeat }
                }
              }
              div("form-email") {
                input {
                  id = "email-input"
                  name = "email"
                  placeholder = configs.getValue("emailPlaceholder") // "user@example.com"
                  type = InputType.email
                  autoFocus = true
                }
              }
              button(classes = "submit") {
                id = "submit-button"
                type = ButtonType.button
                +LocalizedString("Check in at ${location.name}", "Check in bei ${location.name}").get(this@userFrontend)
              }
              div("form-acceptTos") {
                checkBoxInput {
                  name = "accept-tos"
                  id = "accept-tos-checkbox"
                }
                div {
                  label {
                    htmlFor = "accept-tos-checkbox"
                    val tosText = configs.getValue("userTosText")
                    if (tosText.contains("<") && tosText.contains(">")) {
                      +tosText.substringBefore("<")
                      a {
                        target = "_blank"
                        href = configs.getValue("userTosUrl")
                        +tosText.substringAfter("<").substringBefore(">")
                      }
                      +tosText.substringAfter(">")
                    } else {
                      a {
                        target = "_blank"
                        href = configs.getValue("userTosUrl")
                        +tosText
                      }
                    }
                  }
                }
              }
            }
            div("results") {
              div("result-wrapper hidden") {
                div("result hidden") {
                  id = "result-ok"
                  div("header") {
                    span {
                      +LocalizedString("Check-in successful!", "Check-in erfolgreich!").get(this@userFrontend)
                    }
                  }
                  div("large") {
                    div("location") {
                      img {
                        src = "/static/userFrontend/locationIcon.svg"
                      }
                      span {
                        +locationNameWithSeat
                      }
                    }
                    if (showVerificationAnimation) {div("number") {
                      unsafe {
                        +"""
                          <svg>
                            <symbol id="s-text">
                              <text text-anchor="middle" x="50%" y="90%">${now.date.toString().padStart(2, '0')}</text>
                            </symbol>
        
                            <g class = "g-ants">
                              <use xlink:href="#s-text" class="text-copy"></use>
                              <use xlink:href="#s-text" class="text-copy"></use>
                              <use xlink:href="#s-text" class="text-copy"></use>
                              <use xlink:href="#s-text" class="text-copy"></use>
                              <use xlink:href="#s-text" class="text-copy"></use>
                            </g>
                          </svg>
                        """.trimIndent()
                      }
                    }
                    div("verification") {
                      span {
                        +LocalizedString("Verification", "Verifizierung").get(this@userFrontend)}
                      }
                    }
                  }
                  div("details") {
                    div("row") {
                      span("datetime") {
                        +LocalizedString(" at ", " um ").get(this@userFrontend)
                      }
                    }
                    div("row") {
                      span("identification") {
                        id = "result-ok-id"
                      }
                    }
                  }
                }
              }
              span("result fail hidden") {
                id = "result-forbidden-access-restricted"
                +LocalizedString(
                  "Error! You are not allowed to check-in.",
                  "Fehler! Sie haben keine Berechtigung um einzuchecken."
                ).get(this@userFrontend)
              }
              span("result fail hidden") {
                id = "result-forbidden-email"
                +LocalizedString(
                  "Error! Please use your university e-mail address to check-in.",
                  "Fehler! Bitte benutzen Sie ihre Hochschul E-Mail Adresse um einzuchecken."
                ).get(this@userFrontend)
              }
              span("result fail hidden") {
                id = "result-net-err"
                +LocalizedString(
                  "Error! Please try again.",
                  "Fehler! Bitte versuchen Sie es erneut."
                ).get(this@userFrontend)
              }
            }
            div("hidden") {
              id = "checkins-wrapper"
              h3 {
                +LocalizedString("Active check-ins", "Aktive check-ins").get(this@userFrontend)
              }
              div("current-check-in hidden") {
                span {
                  +""
                }
                button {
                  +LocalizedString("Check out", "Auschecken").get(this@userFrontend)
                }
              }
            }
          }
        }
        footer {
          val userFooterAdditionalInfoUrl = configs.getValue("userFooterAdditionalInfoUrl")
          if (userFooterAdditionalInfoUrl.isNotEmpty()) {
            a {
              id = "what-if-infected"
              target = "_blank"
              href = userFooterAdditionalInfoUrl
              +configs.getValue("userFooterAdditionalInfoText")
            }
          }
          button {
            id = "lang-select"
            value = when (language) {
              "en" -> "de"
              else -> "en"
            }
            +when (language) {
              "en" -> "Auf Deutsch ansehen"
              else -> "Switch to English"
            }
          }
          a {
            target = "_blank"
            href = configs.getValue("imprintUrl")
            +configs.getValue("imprintText")
          }
        }
      }
    }
  }
}
