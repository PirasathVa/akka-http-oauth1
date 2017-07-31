package com.github.dafutils.authentication

/**
  * Used for resolving the known credentials corresponding to an OAuth client key
  */
trait KnownOAuthCredentialsSupplier {
  def oauthCredentialsFor(oauthKey: String): Option[OAuthCredentials]
}
