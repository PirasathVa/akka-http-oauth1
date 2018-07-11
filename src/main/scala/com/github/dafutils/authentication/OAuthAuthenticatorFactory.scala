package com.github.dafutils.authentication

import java.time.Instant

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.SecurityDirectives.AuthenticationResult
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class OAuthAuthenticatorFactory(credentialsSupplier: KnownOAuthCredentialsSupplier,
                                authorizationTokenGenerator: AuthorizationTokenGenerator,
                                oauthSignatureParser: OauthSignatureParser,
                                maxTimestampAge: Duration) extends StrictLogging {

  val oauthConsumerKeyParameterName = "oauth_consumer_key"

  val oauthTimestampParameterName = "oauth_timestamp"

  val oauthNonceParameterName = "oauth_nonce"

  val oauthSignatureParameterName = "oauth_signature"

  def containRequiredParameters(callerCredentials: HttpCredentials): Boolean = {
    callerCredentials.getParams().containsKey(oauthConsumerKeyParameterName) &&
      callerCredentials.getParams().containsKey(oauthTimestampParameterName) &&
      callerCredentials.getParams().containsKey(oauthNonceParameterName) &&
      callerCredentials.getParams().containsKey(oauthSignatureParameterName)
  }

  def timestampHasExpired(requestTimestamp: String)= {
    (Instant.now.getEpochSecond - requestTimestamp.toLong) > maxTimestampAge.toSeconds
  }

  def authenticatorFunction(requestHttpMethodName: String, requestUrl: String)
                           (credentialsInRequest: Option[HttpCredentials])
                           (implicit ex: ExecutionContext): Future[AuthenticationResult[OAuthCredentials]] =
    Future {
      credentialsInRequest match {

        case Some(callerCredentials) if containRequiredParameters(callerCredentials) &&
          timestampHasExpired(callerCredentials.params(oauthTimestampParameterName)) => 
          logger.debug(s"Failed authenticating incoming request: The timestamp ${callerCredentials.params(oauthTimestampParameterName)} is too old.")
          Left(HttpChallenge(scheme = "OAuth", realm = None))
        case Some(callerCredentials) if containRequiredParameters(callerCredentials) =>

          val clientKeyInRequest = callerCredentials.params(oauthConsumerKeyParameterName)
          val oauthCredentials = credentialsSupplier.oauthCredentialsFor(clientKeyInRequest)

          oauthCredentials map { knownOauthCredentialsForRequest =>

            val expectedOAuthTokenParameters = expectedOauthParameters(
              requestHttpMethodName,
              requestUrl,
              callerCredentials.params(oauthTimestampParameterName),
              callerCredentials.params(oauthNonceParameterName),
              knownOauthCredentialsForRequest
            )
            (expectedOAuthTokenParameters, knownOauthCredentialsForRequest)
          } map { case (expectedOAuthTokenParameters, knownCredentials) =>
            if (expectedOAuthTokenParameters.oauthSignature == callerCredentials.params(oauthSignatureParameterName)) {
              logger.debug(s"Successfully authenticated incoming request with clientKey=$clientKeyInRequest.")
              Right(knownCredentials)
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

        case _ =>
          logger.debug("Failed authenticating incoming request: the oauth credentials are missing or do not contain all required OAuth parameteres.")
          Left(HttpChallenge(scheme = "OAuth", realm = None))
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
