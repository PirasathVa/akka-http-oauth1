package com.github.dafutils.authentication

import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.SecurityDirectives.AuthenticationResult
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class OAuthAuthenticatorFactory(credentialsSupplier: KnownOAuthCredentialsSupplier,
                                authorizationTokenGenerator: AuthorizationTokenGenerator,
                                oauthSignatureParser: OauthSignatureParser, 
                                requestHttpMethodName: String, 
                                requestUrl: String)
                               (implicit ex: ExecutionContext) extends StrictLogging {

  val oauthConsumerKeyParameterName = "oauth_consumer_key"

  val oauthTimestampParameterName = "oauth_timestamp"

  val oauthNonceParameterName = "oauth_nonce"

  val oauthSignatureParameterName = "oauth_signature"
  
  def authenticatorFunction: Option[HttpCredentials] => Future[AuthenticationResult[OAuthCredentials]] = 
    (credentialsInRequest: Option[HttpCredentials]) => 
    Future {
      credentialsInRequest match {

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
          logger.debug("Failed authenticating incoming request: the oauth credentials are missing or do not contain all required OAuth paramteres.")
          Left(HttpChallenge(scheme = "OAuth", realm = None))
      }
    }

  private def containRequiredParameters(callerCredentials: HttpCredentials): Boolean = {
    callerCredentials.getParams().containsKey(oauthConsumerKeyParameterName) &&
      callerCredentials.getParams().containsKey(oauthTimestampParameterName) &&
      callerCredentials.getParams().containsKey(oauthNonceParameterName) &&
      callerCredentials.getParams().containsKey(oauthSignatureParameterName)
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
