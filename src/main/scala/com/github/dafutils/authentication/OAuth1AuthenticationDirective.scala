package com.github.dafutils.authentication

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{authenticateOrRejectWithChallenge, extractExecutionContext, extractRequest}
import com.github.dafutils.authentication

import scala.concurrent.duration._

object OAuth1AuthenticationDirective {
  val authorizationTokenGenerator = new AuthorizationTokenGenerator()
  val oauthSignatureParser = new OauthSignatureParser()

  def apply(credentialsSupplier: KnownOAuthCredentialsSupplier,
            authorizationTokenGenerator: AuthorizationTokenGenerator = new AuthorizationTokenGenerator(),
            oauthSignatureParser: OauthSignatureParser = new OauthSignatureParser(),
            maxTimestampAge: Duration = 30 seconds)(implicit as: ActorSystem): Directive1[authentication.OAuthCredentials] = {

    implicit lazy val log: LoggingAdapter = Logging(as, this.getClass)
    val authenticationFactory = new OAuthAuthenticatorFactory(
      credentialsSupplier,
      authorizationTokenGenerator,
      oauthSignatureParser,
      maxTimestampAge
    )

    extractExecutionContext flatMap { implicit ec =>
      extractRequest flatMap { httpRequest =>
        authenticateOrRejectWithChallenge(
          authenticationFactory.authenticatorFunction(
            requestHttpMethodName = httpRequest.method.value,
            requestUrl = urlUsedToSign(httpRequest)
          ) _
        )
      }
    }
  }

  private def urlUsedToSign(httpRequest: HttpRequest)(implicit log: LoggingAdapter): String = {
    val protocolFromHeader = httpRequest.headers
      .find(_.name equalsIgnoreCase "x-forwarded-proto")
      .map(_.value)

    val schemeUsedToSign = protocolFromHeader.getOrElse(httpRequest.uri.scheme)
    val urlUsedToSign = httpRequest.uri.copy(scheme = schemeUsedToSign).toString()
    log.debug(s"Url used to sign incoming request: $urlUsedToSign")
    urlUsedToSign
  }
}
