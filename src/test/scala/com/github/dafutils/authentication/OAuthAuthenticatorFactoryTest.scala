package com.github.dafutils.authentication

import akka.http.scaladsl.model.headers.{GenericHttpCredentials, HttpChallenge}
import akka.http.scaladsl.server.Directives.AuthenticationResult
import org.apache.http.client.methods.HttpGet
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import util.UnitTestSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuthAuthenticatorFactoryTest extends UnitTestSpec {

  behavior of "OAuthAuthenticatorFactory"

  private val credentialsSupplierMock = mock[KnownOAuthCredentialsSupplier]
  private val authorizationTokenGeneratorMock = mock[AuthorizationTokenGenerator]
  private val signatureParserMock = mock[OauthSignatureParser]

  it should "authenticate successfully if the server generated signature matches the one of the incoming request" in {
    //Given
    val testRequestHttpMethodName = HttpGet.METHOD_NAME
    val testRequestUrl = "http://example.com"
    val testRequestClientKey = "testRequestClientKey"
    val testRequestClientSecret = "testRequestClientSecret"
    val testRequestCredentials = OAuthCredentials(
      clientKey = testRequestClientKey,
      clientSecret = testRequestClientSecret
    )

    val incomingRequestTimestamp = "12342352341"
    val incomingRequestNonce = "testNonce"
    val incomingRequestSignature = "incomingRequestSignature"
    val testHttpCredentials = GenericHttpCredentials(
      scheme = "",
      params = Map(
        "oauth_consumer_key" -> testRequestClientKey,
        "oauth_timestamp" -> incomingRequestTimestamp,
        "oauth_nonce" -> incomingRequestNonce,
        "oauth_signature" -> incomingRequestSignature
      )
    )
    val mockConnectorGeneratedToken = "mockToken"

    when {
      authorizationTokenGeneratorMock.generateAuthorizationHeader(
        httpMethodName = testRequestHttpMethodName,
        resourceUrl = testRequestUrl,
        timeStamp = incomingRequestTimestamp,
        nonce = incomingRequestNonce,
        oauthCredentials = testRequestCredentials
      )
    } thenReturn mockConnectorGeneratedToken

    val serverGeneratedSignatureParams = OauthParameters(
      consumerKey = testRequestClientKey,
      timestamp = incomingRequestTimestamp,
      nonce = incomingRequestNonce,
      oauthSignature = incomingRequestSignature
    )

    when {
      signatureParserMock.parse(mockConnectorGeneratedToken)
    } thenReturn serverGeneratedSignatureParams

    when {
      credentialsSupplierMock.oauthCredentialsFor(testRequestClientKey)
    } thenReturn Some(testRequestCredentials)

    val tested = new OAuthAuthenticatorFactory(credentialsSupplierMock, authorizationTokenGeneratorMock, signatureParserMock, testRequestHttpMethodName, testRequestUrl)
    //When
    val authenticationResult: Future[AuthenticationResult[OAuthCredentials]] = tested.authenticatorFunction(Some(testHttpCredentials))

    //Then
    whenReady(
      future = authenticationResult,
      timeout = Timeout(5 seconds)
    ) {
      _ shouldEqual Right(testRequestCredentials)
    }
  }

  it should "fail to authenticate if the server generated signature does not match the one of the incoming request" in {
    //Given
    val testRequestHttpMethodName = HttpGet.METHOD_NAME
    val testRequestUrl = "http://example.com"
    val testRequestClientKey = "testRequestClientKey"
    val testRequestClientSecret = "testRequestClientSecret"
    val testRequestCredentials = OAuthCredentials(
      clientKey = testRequestClientKey,
      clientSecret = testRequestClientSecret
    )

    val incomingRequestTimestamp = "12342352341"
    val incomingRequestNonce = "testNonce"
    val incomingRequestSignature = "incomingRequestSignature"
    val testHttpCredentials = GenericHttpCredentials(
      scheme = "",
      params = Map(
        "oauth_consumer_key" -> testRequestClientKey,
        "oauth_timestamp" -> incomingRequestTimestamp,
        "oauth_nonce" -> incomingRequestNonce,
        "oauth_signature" -> incomingRequestSignature
      )
    )
    val mockConennctorGeneratedToken = "mockToken"

    when {
      authorizationTokenGeneratorMock.generateAuthorizationHeader(
        httpMethodName = testRequestHttpMethodName,
        resourceUrl = testRequestUrl,
        timeStamp = incomingRequestTimestamp,
        nonce = incomingRequestNonce,
        oauthCredentials = testRequestCredentials
      )
    } thenReturn mockConennctorGeneratedToken

    val serverGeneratedSignatureParams = OauthParameters(
      consumerKey = testRequestClientKey,
      timestamp = incomingRequestTimestamp,
      nonce = incomingRequestNonce,
      oauthSignature = incomingRequestSignature + "fsdfs"
    )

    when {
      signatureParserMock.parse(mockConennctorGeneratedToken)
    } thenReturn serverGeneratedSignatureParams

    when {
      credentialsSupplierMock.oauthCredentialsFor(testRequestClientKey)
    } thenReturn Some(testRequestCredentials)
    val tested = new OAuthAuthenticatorFactory(credentialsSupplierMock, authorizationTokenGeneratorMock, signatureParserMock, testRequestHttpMethodName, testRequestUrl)
    
    //When
    val authenticationResult: Future[AuthenticationResult[OAuthCredentials]] = tested.authenticatorFunction(Some(testHttpCredentials))

    //Then
    whenReady(
      future = authenticationResult,
      timeout = Timeout(5 seconds)
    ) {
      _ shouldEqual Left(HttpChallenge(scheme = "OAuth", realm = None))
    }
  }

  it should "fail to authenticate if the server generated signature does not contain all required OAuth parameters" in {
    //Given
    val testRequestHttpMethodName = HttpGet.METHOD_NAME
    val testRequestUrl = "http://example.com"

    val incomingRequestTimestamp = "12342352341"
    val incomingRequestNonce = "testNonce"
    val incomingRequestSignature = "incomingRequestSignature"
    val testHttpCredentials = GenericHttpCredentials(
      scheme = "",
      params = Map(
        "oauth_timestamp" -> incomingRequestTimestamp,
        "oauth_nonce" -> incomingRequestNonce,
        "oauth_signature" -> incomingRequestSignature
      )
    )
    val tested = new OAuthAuthenticatorFactory(credentialsSupplierMock, authorizationTokenGeneratorMock, signatureParserMock, testRequestHttpMethodName, testRequestUrl)
    
    //When
    val authenticationResult: Future[AuthenticationResult[OAuthCredentials]] = tested.authenticatorFunction(Some(testHttpCredentials))

    //Then
    whenReady(
      future = authenticationResult,
      timeout = Timeout(5 seconds)
    ) {
      _ shouldEqual Left(HttpChallenge(scheme = "OAuth", realm = None))
    }
  }


  it should "fail authentication if no credentials are provided" in {
    //Given
    val testRequestHttpMethodName = HttpGet.METHOD_NAME
    val testRequestUrl = "http://example.com"

    val tested = new OAuthAuthenticatorFactory(credentialsSupplierMock, authorizationTokenGeneratorMock, signatureParserMock, testRequestHttpMethodName, testRequestUrl)
    
    //When
    val authenticationResult: Future[AuthenticationResult[OAuthCredentials]] = tested.authenticatorFunction(None)

    //Then
    whenReady(
      future = authenticationResult,
      timeout = Timeout(5 seconds)
    ) {
      _ shouldEqual Left(HttpChallenge(scheme = "OAuth", realm = None))
    }
  }
}
