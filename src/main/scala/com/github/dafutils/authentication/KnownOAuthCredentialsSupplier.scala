package com.github.dafutils.authentication

trait KnownOAuthCredentialsSupplier {
  def oauthCredentialsFor(oauthKey: String): Option[OAuthCredentials]
}
