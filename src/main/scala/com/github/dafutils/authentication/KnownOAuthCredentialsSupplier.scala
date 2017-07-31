package com.github.dafutils.authentication

trait KnownOAuthCredentialsSupplier {
  def oauthSecretFor(oauthKey: String): OAuthCredentials
}
