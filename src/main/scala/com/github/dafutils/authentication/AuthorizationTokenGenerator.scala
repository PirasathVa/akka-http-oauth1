package com.github.dafutils.authentication

import com.typesafe.scalalogging.StrictLogging
import oauth.signpost.OAuthConsumer
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import org.apache.http.client.methods._

class AuthorizationTokenGenerator extends StrictLogging {

  /**
    * For a pair of HTTP method name and url, generate the appropriate "Authorization" header value
    *
    * @param httpMethodName the HTTP method name for which we need to generate an authorization value
    * @param resourceUrl    the resource for which we want to generate an authorization value
    * @return The OAuth 1.0 token value
    */
  def generateAuthorizationHeaderValue(httpMethodName: String,
                                       resourceUrl: String,
                                       oauthCredentials: OAuthCredentials): String = {
    signWithConsumer(
      new CommonsHttpOAuthConsumer(
        oauthCredentials.clientKey,
        oauthCredentials.clientSecret
      )
    )(httpMethodName, resourceUrl)
  }

  /**
    * Generate the signature, with a pre-defined value of the timestamp and the nonce.
    * Useful for validating incoming requests
    *
    * @param httpMethodName the http method name used to create the signature
    * @param resourceUrl the url used to create the signature
    * @param timeStamp the timestamp used to sign (usually taken from a request we want to validate)
    * @param nonce the nonce used to sign (usually taken from a request we want to validate)
    * @param oauthCredentials the key/secret pair that we use to sign
    * @return The OAuth 1.0 token value
    */
  def generateAuthorizationHeader(httpMethodName: String,
                                  resourceUrl: String,
                                  timeStamp: String,
                                  nonce: String,
                                  oauthCredentials: OAuthCredentials): String = 
    signWithConsumer(
      new ValidatingOAuthConsumer(
        consumerKey = oauthCredentials.clientKey,
        consumerSecret = oauthCredentials.clientSecret,
        nonce = nonce,
        timestamp = timeStamp
      )
    )(httpMethodName, resourceUrl)

  private def signWithConsumer(consumer: OAuthConsumer)
                              (httpMethodName: String, resourceUrl: String): String = {

    val request = httpMethodName match {
      case requestMethod if requestMethod.equalsIgnoreCase(HttpGet.METHOD_NAME) => new HttpGet(resourceUrl)
      case requestMethod if requestMethod.equalsIgnoreCase(HttpPost.METHOD_NAME) => new HttpPost(resourceUrl)
      case requestMethod if requestMethod.equalsIgnoreCase(HttpPut.METHOD_NAME) => new HttpPut(resourceUrl)
      case requestMethod if requestMethod.equalsIgnoreCase(HttpDelete.METHOD_NAME) => new HttpDelete(resourceUrl)
      case requestMethod if requestMethod.equalsIgnoreCase(HttpHead.METHOD_NAME) => new HttpHead(resourceUrl)
      case requestMethod if requestMethod.equalsIgnoreCase(HttpOptions.METHOD_NAME) => new HttpOptions(resourceUrl)
      case requestMethod if requestMethod.equalsIgnoreCase(HttpTrace.METHOD_NAME) => new HttpTrace(resourceUrl)
      case _ => throw new IllegalArgumentException(s"Request method $httpMethodName is not supported")
    }

    consumer.sign(request)

    val authorizationHeader = request.getHeaders("Authorization")(0)

    authorizationHeader.getValue
  }
}
