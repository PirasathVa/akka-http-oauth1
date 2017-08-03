package com.github.dafutils.authentication

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.BasicHttpEntity
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

  it should "authenticate successfully if the signed url contains a url parameter" in {
    //Given
    val testKey = "testKey"
    val testSecret = "testSecret"
    val request = signedGetRequestWithUrlParams(testKey, testSecret)

    when {
      supplierMock.oauthCredentialsFor(testKey)
    } thenReturn {
      Some(OAuthCredentials(testKey, testSecret))
    }

    request ~> testedRoute ~> check {
      response.status shouldBe OK
    }
  }

  def signedGetRequestWithHeaders(testKey: String, testSecret: String): HttpRequest = {
    val consumer = new CommonsHttpOAuthConsumer(testKey, testSecret)
    val getRequest = new HttpGet("http://example.com")
    val customHeaderName = "CustomHeader"
    val customHeaderValue = UUID.randomUUID().toString
    getRequest.setHeader(customHeaderName, customHeaderValue)

    val signedRequest = consumer.sign(getRequest)

    val authorizationHeader = Authorization(
      GenericHttpCredentials(
        scheme = "OAuth",
        params = oauthParser.parseAsMap(
          signedRequest.getHeader("Authorization")
        )
      )
    )

    val customAkkaHttpHeader = RawHeader(customHeaderName, customHeaderValue)
    Get(uri = "http://example.com").withHeaders(authorizationHeader, customAkkaHttpHeader)
  }

  it should "authenticate successfully if the signed url contains headers" in {
    //Given
    val testKey = "testKey"
    val testSecret = "testSecret"
    val request = signedGetRequestWithHeaders(testKey, testSecret)

    when {
      supplierMock.oauthCredentialsFor(testKey)
    } thenReturn {
      Some(OAuthCredentials(testKey, testSecret))
    }

    request ~> testedRoute ~> check {
      response.status shouldBe OK
    }
  }

  def signedPostRequestWithBody(testKey: String, testSecret: String): HttpRequest = {
    val consumer = new CommonsHttpOAuthConsumer(testKey, testSecret)
    val urlUsedForSign = "http://example.com"
    
    val postRequest = new HttpPost(urlUsedForSign)
    val entity = new BasicHttpEntity()
    entity.setContent(new ByteArrayInputStream("SomeREquestBody".getBytes(StandardCharsets.UTF_8)))
    postRequest.setEntity(entity)
    
    val signedRequest = consumer.sign(postRequest)
    val authorizationHeaderValue = signedRequest.getHeader("Authorization")

    val authorizationHeader = Authorization(
      GenericHttpCredentials(
        scheme = "OAuth",
        params = oauthParser.parseAsMap(authorizationHeaderValue)
      )
    )

    Post(uri = urlUsedForSign).withHeaders(authorizationHeader)
  }

  it should "authenticate successfully if the signed request contains body" in {
    //Given
    val testKey = "testKey"
    val testSecret = "testSecret"
    val request = signedPostRequestWithBody(testKey, testSecret)

    when {
      supplierMock.oauthCredentialsFor(testKey)
    } thenReturn {
      Some(OAuthCredentials(testKey, testSecret))
    }

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

  private def signedGetRequestWithUrlParams(testKey: String, testSecret: String): HttpRequest = {
    val consumer = new CommonsHttpOAuthConsumer(testKey, testSecret)
    val urlWithParameters = "http://example.com?urlParams=true"
    val getRequest = new HttpGet(urlWithParameters)
    val signedRequest = consumer.sign(getRequest)
    val authorizationHeaderValue = signedRequest.getHeader("Authorization")

    val authorizationHeader = Authorization(
      GenericHttpCredentials(
        scheme = "OAuth",
        params = oauthParser.parseAsMap(authorizationHeaderValue)
      )
    )

    Get(uri = urlWithParameters).withHeaders(authorizationHeader)
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
