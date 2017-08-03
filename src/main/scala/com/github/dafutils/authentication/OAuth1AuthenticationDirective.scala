package com.github.dafutils.authentication

import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{authenticateOrRejectWithChallenge, extractRequest}
import com.github.dafutils.authentication

import scala.concurrent.ExecutionContext

object OAuth1AuthenticationDirective {
  val authorizationTokenGenerator = new AuthorizationTokenGenerator()
  val oauthSignatureParser = new OauthSignatureParser()

  def apply(credentialsSupplier: KnownOAuthCredentialsSupplier)
           (implicit ec: ExecutionContext): Directive1[authentication.OAuthCredentials] = {

    val authenticationFactory = new OAuthAuthenticatorFactory(
      credentialsSupplier,
      authorizationTokenGenerator,
      oauthSignatureParser
    )

    extractRequest flatMap { httpRequest =>

      authenticateOrRejectWithChallenge(
        authenticationFactory.authenticatorFunction(
          requestHttpMethodName = httpRequest.method.value,
          requestUrl = urlUsedToSign(httpRequest)
        ) _
      )
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
