package com.github.dafutils.authentication

import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import org.apache.http.client.methods.HttpGet
import org.mockito.Mockito.when
import util.UnitTestSpec

class OAuth1AuthenticationDirectiveTest extends UnitTestSpec with ScalatestRouteTest {

  behavior of "OAuth1AuthenticationDirective"

  val oauthParser = new OauthSignatureParser
  val supplierMock: KnownOAuthCredentialsSupplier = mock[KnownOAuthCredentialsSupplier]
  val testedDirective: Directive1[OAuthCredentials] = OAuth1AuthenticationDirective(supplierMock)
  val testedRoute: Route = testedDirective { authenticatedCreds =>
    complete {
      HttpResponse()
    }
  }

  it should "authenticate successfully on a non-forwarded req" in {
    //Given
    val testKey = "testKey"
    val testSecret = "testSecret"
    val request = signedGetRequest(testKey, testSecret)

    when {
      supplierMock.oauthCredentialsFor(testKey)
    } thenReturn {
      Some(OAuthCredentials(testKey, testSecret))
    }

    //When
    request ~> testedRoute ~> check {
      response.status shouldBe OK
    }
  }

  it should "authenticate successfully on a forwarded request with appropriate x-forwarder-proto header" in {
    //Given
    val testKey = "testKey"
    val testSecret = "testSecret"
    val request = signedGetRequestRedirectedFromHttps(testKey, testSecret)

    when {
      supplierMock.oauthCredentialsFor(testKey)
    } thenReturn {
      Some(OAuthCredentials(testKey, testSecret))
    }

    //When
    request ~> testedRoute ~> check {
      response.status shouldBe OK
    }
  }

  it should "fail authenticating on a forwarded request missing the x-forwarder-proto header" in {
    //Given
    val testKey = "testKey"
    val testSecret = "testSecret"
    val request = signedGetRequestRedirectedFromHttps(testKey, testSecret)

    when {
      supplierMock.oauthCredentialsFor(testKey)
    } thenReturn {
      Some(OAuthCredentials(testKey, testSecret))
    }

    //When
    request ~> testedRoute ~> check {
      response.status shouldBe OK
    }
  }
  
  private def signedGetRequest(testKey: String, testSecret: String): HttpRequest = {
    val consumer = new CommonsHttpOAuthConsumer(testKey, testSecret)
    val getRequest = new HttpGet("http://example.com")
    val signedRequest = consumer.sign(getRequest)
    val authorizationHeaderValue = signedRequest.getHeader("Authorization")
    
    val authorizationHeader = Authorization(
      GenericHttpCredentials(
        scheme = "OAuth", 
        params = oauthParser.parseAsMap(authorizationHeaderValue)
      )
    )

    Get(uri = "http://example.com").withHeaders(authorizationHeader)
  }

  private def signedGetRequestRedirectedFromHttps(testKey: String, testSecret: String): HttpRequest = {
    val consumer = new CommonsHttpOAuthConsumer(testKey, testSecret)
    val getRequest = new HttpGet("https://example.com")
    val signedRequest = consumer.sign(getRequest)
    val authorizationHeaderValue = signedRequest.getHeader("Authorization")
    
    val authorizationHeader = Authorization(
      GenericHttpCredentials(
        scheme = "OAuth", 
        params = oauthParser.parseAsMap(authorizationHeaderValue)
      )
    )
    val xForwardedProto = RawHeader("x-forwarded-proto", "https")
    Get(uri = "http://example.com").withHeaders(authorizationHeader, xForwardedProto)
  }

  private def signedForwardedGetRequestWithoutProtoHeader(testKey: String, testSecret: String): HttpRequest = {
    val consumer = new CommonsHttpOAuthConsumer(testKey, testSecret)
    val getRequest = new HttpGet("https://example.com")
    val signedRequest = consumer.sign(getRequest)
    val authorizationHeaderValue = signedRequest.getHeader("Authorization")

    val parsed = oauthParser.parseAsMap(authorizationHeaderValue)
    val authorizationHeader = Authorization(GenericHttpCredentials(scheme = "OAuth", params = parsed))
    Get(uri = "http://example.com").withHeaders(authorizationHeader)
  }
}
