package com.studo.campusqr.endpoints

import com.studo.campusqr.common.ClientLocation
import com.studo.campusqr.common.LocationVisitData
import com.studo.campusqr.common.extensions.emailRegex
import com.studo.campusqr.database.BackendLocation
import com.studo.campusqr.database.CheckIn
import com.studo.campusqr.database.MainDatabase
import com.studo.campusqr.extensions.*
import com.studo.campusqr.utils.getSessionToken
import com.studo.campusqr.utils.getUser
import com.studo.campusqr.utils.isAuthenticated
import com.studo.katerbase.equal
import io.ktor.application.*
import io.ktor.http.*
import java.util.*

/**
 * This file contains every endpoint which is used in the location management.
 */
suspend fun getLocation(id: String): BackendLocation {
  return getLocationOrNull(id) ?: throw IllegalArgumentException("No location for id")
}

suspend fun getLocationOrNull(id: String): BackendLocation? {
  return runOnDb { getCollection<BackendLocation>().findOne(BackendLocation::_id equal id) }
}

suspend fun getAllLocations(language: String): List<ClientLocation> {
  return runOnDb { getCollection<BackendLocation>().find().toList() }.map { it.toClientClass(language) }
}

suspend fun ApplicationCall.createLocation() {
  if (!getSessionToken().isAuthenticated) {
    respondForbidden()
    return
  }

  val params = receiveJsonMap()

  val name = params["name"]?.trim() ?: throw IllegalArgumentException("No name was provided")

  val room = BackendLocation().apply {
    this._id = randomId().take(20) // 20 Characters per code to make it better detectable
    this.name = name
    this.createdDate = Date()
    this.createdBy = getUser()._id
    this.checkInCount = 0
  }

  MainDatabase.getCollection<BackendLocation>().insertOne(room, upsert = false)

  respondOk()
}

suspend fun ApplicationCall.listLocations() {
  if (!getSessionToken().isAuthenticated) {
    respondForbidden()
    return
  }

  val locations = getAllLocations(language)
  respondObject(locations)
}

suspend fun ApplicationCall.visitLocation() {
  val params = receiveJsonMap()

  val location = parameters["id"]?.let { getLocation(it) }
      ?: throw IllegalArgumentException("No locationId was provided")

  val email = params["email"]?.trim() ?: throw IllegalArgumentException("No email was provided")
  val now = Date()

  // Clients can send a custom visit date
  // This is useful for offline dispatching, we want to save the date of the visit and not when the
  // request arrives on the server.
  val visitDate = params["date"]
    ?.let { Date(it.toLong()) }

  // Don't allow dates older than 7 days
  if (visitDate != null && visitDate > now.addDays(days = -7)) {
    respondOk()
    return
  }

  if (!email.matches(emailRegex) || email.count() > 100) {
    respondError("email_not_valid") // At least do a very basic email validation to catch common errors
    return
  }

  val checkIn = CheckIn().apply {
    this._id = randomId()
    this.locationId = location._id
    this.date = visitDate ?: now
    this.email = email
    this.userAgent = request.headers[HttpHeaders.UserAgent] ?: ""
  }

  runOnDb {
    getCollection<CheckIn>().insertOne(checkIn, upsert = false)

    getCollection<BackendLocation>().updateOne(BackendLocation::_id equal location._id) {
      BackendLocation::checkInCount incrementBy 1
    }
  }

  respondOk()
}

suspend fun ApplicationCall.returnLocationVisitCsvData() {
  if (!getSessionToken().isAuthenticated) {
    respondForbidden()
    return
  }

  val locationId = parameters["id"]!!

  val location = runOnDb { getLocation(locationId) }

  val checkIns = runOnDb {
    getCollection<CheckIn>()
      .find(CheckIn::locationId equal locationId)
      .sortByDescending(CheckIn::date)
      .toList()
  }

  respondObject(
    LocationVisitData(
      csvData = "sep=;\n" + checkIns.joinToString(separator = "\n") { "${it.email};${it.date.toAustrianTime()}" },
      csvFileName = "${location.name}.csv"
    )
  )
}

suspend fun ApplicationCall.editLocation() {
  if (!getSessionToken().isAuthenticated) {
    respondForbidden()
    return
  }

  val locationId = parameters["id"]!!

  val params = receiveJsonMap()

  val newName = params["name"]?.trim()

  runOnDb {
    getCollection<BackendLocation>().updateOne(BackendLocation::_id equal locationId) {
      if (newName != null) {
        BackendLocation::name setTo newName
      }
    }
  }

  respondOk()
}

suspend fun ApplicationCall.deleteLocation() {
  if (!getSessionToken().isAuthenticated) {
    respondForbidden()
    return
  }

  val locationId = parameters["id"]!!

  runOnDb {
    getCollection<BackendLocation>().deleteOne(BackendLocation::_id equal locationId)
    getCollection<CheckIn>().deleteMany(CheckIn::locationId equal locationId)
  }

  respondOk()
}