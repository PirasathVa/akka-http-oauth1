package com.github.dafutils.authentication

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.SecurityDirectives.AuthenticationResult
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class OAuthAuthenticatorFactory(credentialsSupplier: KnownOAuthCredentialsSupplier,
                                authorizationTokenGenerator: AuthorizationTokenGenerator,
                                oauthSignatureParser: OauthSignatureParser) extends StrictLogging {

  def authenticatorFunction(requestHttpMethodName: String, requestUrl: String)
                           (credentialsInRequest: Option[HttpCredentials])
                           (implicit ex: ExecutionContext): Future[AuthenticationResult[OAuthCredentials]] =
    Future {
      credentialsInRequest match {
        case None =>
          logger.debug("Failed authenticating incoming request: no credentials present.")
          Left(HttpChallenge(scheme = "OAuth", realm = None))

        case Some(callerCredentials) =>

          val clientKeyInRequest = callerCredentials.params("oauth_consumer_key")
          val oauthCredentials = credentialsSupplier.oauthCredentialsFor(clientKeyInRequest)
          
          oauthCredentials map { knownOauthCredentialsForRequest =>

            val expectedOAuthTokenParameters = expectedOauthParameters(
              requestHttpMethodName,
              requestUrl,
              callerCredentials.params("oauth_timestamp"),
              callerCredentials.params("oauth_nonce"),
              knownOauthCredentialsForRequest
            )
            (expectedOAuthTokenParameters, knownOauthCredentialsForRequest)
          } map { case (expectedOAuthTokenParameters, param) =>
            if (expectedOAuthTokenParameters.oauthSignature == callerCredentials.params("oauth_signature")) {
              logger.debug(s"Successfully authenticated incoming request with clientKey=$clientKeyInRequest.")
              Right(param)
            } else {
              logger.debug(s"Failed authenticating incoming request with clientKey=$clientKeyInRequest: Signature does not match expected one.")
              Left(HttpChallenge(scheme = "OAuth", realm = None))
            }
          } getOrElse {
            logger.debug(
              s"Failed authenticating incoming request with clientKey=$clientKeyInRequest: " +
              s"could not resolve corresponding client secret for this client. The client key is likely not known")
            Left(HttpChallenge(scheme = "OAuth", realm = None))
          }
      }
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
