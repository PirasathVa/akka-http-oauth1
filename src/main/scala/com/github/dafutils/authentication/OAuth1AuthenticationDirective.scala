package com.github.dafutils.authentication

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{authenticateOrRejectWithChallenge, extractExecutionContext, extractRequest}
import com.github.dafutils.authentication

object OAuth1AuthenticationDirective {
  val authorizationTokenGenerator = new AuthorizationTokenGenerator()
  val oauthSignatureParser = new OauthSignatureParser()

  def apply(credentialsSupplier: KnownOAuthCredentialsSupplier): Directive1[authentication.OAuthCredentials] = {
    extractExecutionContext flatMap { implicit ec =>
      extractRequest flatMap { httpRequest =>
        val authFactory = new OAuthAuthenticatorFactory(
          credentialsSupplier,
          authorizationTokenGenerator,
          oauthSignatureParser,
          httpRequest.method.value,
          urlUsedToSign(httpRequest)
        )
        authenticateOrRejectWithChallenge(authFactory.authenticatorFunction)
      }
    }
  }

  private def urlUsedToSign(httpRequest: HttpRequest): String = {
    val protocolFromHeader = httpRequest.headers
      .find(_.name equalsIgnoreCase "x-forwarded-proto")
      .map(_.value)

    val schemeUsedToSign = protocolFromHeader.getOrElse(httpRequest.uri.scheme)

    httpRequest.uri.copy(scheme = schemeUsedToSign).toString()
  }
}
