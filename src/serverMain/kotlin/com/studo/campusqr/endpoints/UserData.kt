package com.studo.campusqr.endpoints

import com.studo.campusqr.auth.AuthProvider
import com.studo.campusqr.authProvider
import com.studo.campusqr.baseUrl
import com.studo.campusqr.common.LoginResult
import com.studo.campusqr.common.UserData
import com.studo.campusqr.database.BackendUser
import com.studo.campusqr.database.Configuration
import com.studo.campusqr.database.SessionToken
import com.studo.campusqr.extensions.*
import com.studo.campusqr.utils.Session
import com.studo.campusqr.utils.createNewSessionToken
import com.studo.campusqr.utils.getSessionToken
import com.studo.campusqr.utils.validateCsrfToken
import com.studo.katerbase.equal
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.sessions.*
import java.util.*

/**
 * This file contains every endpoint which is used in the user lifecycle.
 */

private suspend fun getUser(id: String?): BackendUser? {
  if (id == null) return null
  return runOnDb { getCollection<BackendUser>().findOne(BackendUser::_id equal id) }
}

suspend fun ApplicationCall.getUserData() {
  val sessionToken = getSessionToken() ?: createNewSessionToken()
  val user = if (sessionToken.isAuthenticated) getUser(sessionToken.userId) else null

  val appName = runOnDb {
    getCollection<Configuration>().findOne(Configuration::_id equal "appName")?.stringValue ?: ""
  }

  respondObject(UserData().apply {
    this.appName = appName
    this.clientUser = user?.toClientClass(this@getUserData.language)
  })
}

suspend fun ApplicationCall.logout() {
  val sessionToken = getSessionToken()
  if (sessionToken != null) {
    runOnDb {
      getCollection<SessionToken>().updateOne(SessionToken::_id equal sessionToken._id) {
        SessionToken::expiryDate setTo Date()
      }
    }
    sessions.clear<Session>()
  }
  respondRedirect(baseUrl, permanent = false)
}

suspend fun ApplicationCall.login() {
  validateCsrfToken()

  val params = receiveJsonMap()
  val email = params["email"] ?: run {
    respondEnum(LoginResult.LOGIN_FAILED)
    return
  }
  val password = params["password"] ?: run {
    respondEnum(LoginResult.LOGIN_FAILED)
    return
  }

  val loginResult = authProvider.login(email, password)

  if (loginResult is AuthProvider.Result.InvalidCredentials) {
    respondEnum(LoginResult.LOGIN_FAILED)
    return
  }

  // User login was successful
  loginResult as AuthProvider.Result.Success

  runOnDb {
    getCollection<BackendUser>().updateOne(
      BackendUser::_id equal loginResult.user._id,
      BackendUser::firstLoginDate equal null
    ) {
      BackendUser::firstLoginDate setTo Date()
    }
  }

  val sessionToken = (getSessionToken() ?: createNewSessionToken()).apply {
    userId = loginResult.user._id
  }

  runOnDb {
    getCollection<SessionToken>().insertOne(sessionToken, upsert = true)
  }

  respondEnum(LoginResult.LOGIN_SUCCESSFUL)
}