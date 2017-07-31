package com.github.dafutils.authentication

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.SecurityDirectives.AuthenticationResult

import scala.concurrent.{ExecutionContext, Future}

class OAuthAuthenticatorFactory(credentialsSupplier: KnownOAuthCredentialsSupplier,
                                authorizationTokenGenerator: AuthorizationTokenGenerator,
                                oauthSignatureParser: OauthSignatureParser) {

  def authenticatorFunction(requestHttpMethodName: String, requestUrl: String)
                           (credentialsInRequest: Option[HttpCredentials])
                           (implicit ex: ExecutionContext): Future[AuthenticationResult[OAuthCredentials]] =
    Future {
      credentialsInRequest match {
        case None =>
          Left(HttpChallenge(scheme = "OAuth", realm = None))

        case Some(callerCredentials) =>

          val requestClientKey = callerCredentials.params("oauth_consumer_key")
          val oauthCredentials = credentialsSupplier.oauthSecretFor(requestClientKey)
          val expectedOAuthTokenParameters = expectedOauthParameters(
            requestHttpMethodName, 
            requestUrl,
            callerCredentials.params("oauth_timestamp"), 
            callerCredentials.params("oauth_nonce"), 
            oauthCredentials
          )

          if (expectedOAuthTokenParameters.oauthSignature == callerCredentials.params("oauth_signature")) {
            Right(oauthCredentials)
          } else {
            Left(HttpChallenge(scheme = "OAuth", realm = None))
          }
      }
    } recover {
      case _: UnknownClientKeyException => Left(HttpChallenge(scheme = "OAuth", realm = None))
    }

  private def expectedOauthParameters(requestHttpMethodName: String, 
                                      requestUrl: String,
                                      callerTimestamp: String,
                                      callerNonce: String,
                                      connectorCredentials: OAuthCredentials) = {
    val expectedOAuthToken = authorizationTokenGenerator.generateAuthorizationHeader(
      httpMethodName = requestHttpMethodName,
      resourceUrl = requestUrl,
      timeStamp = callerTimestamp,
      nonce = callerNonce,
      oauthCredentials = connectorCredentials
    )
    oauthSignatureParser.parse(expectedOAuthToken)
  }
}
