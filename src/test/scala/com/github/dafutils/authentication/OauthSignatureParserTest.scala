package com.github.dafutils.authentication

import org.scalatest.FunSuite
import util.UnitTestSpec

class OauthSignatureParserTest extends UnitTestSpec {

  behavior of "OauthSignatureParser"


  it should "parse an Oauth header where parameters are separated by spaces" in {
    //Given
    val testKey = "testKey"
    val testNonce = "4719564623415197162"
    val testSignature = "MRY%2Bstos95lsidqI0HqzdXquWo4%3D"
    val testOauthMethod = "HMAC-SHA1"
    val testTimeStamp = "1501687098"
    val testOAuthVersion = "1.0"
    val testOauthHeaderWithSpaces = "OAuth oauth_consumer_key=\"testKey\", oauth_nonce=\"4719564623415197162\", oauth_signature=\"MRY%2Bstos95lsidqI0HqzdXquWo4%3D\", oauth_signature_method=\"HMAC-SHA1\", oauth_timestamp=\"1501687098\", oauth_version=\"1.0\""
  }
}
