package com.austindata

import com.typesafe.config.ConfigFactory

package object orders {
  // Values retrieved from settings.properties
  private val config = ConfigFactory.load("settings.properties")
  lazy val system = config.getString("system")
  lazy val userProfile = config.getString("userProfile")
  lazy val password = config.getString("password")

  // Global values
  val companyCode = "GR" //Change to GR for production
  val libraryPrefix = "T2ADI"
  val orderFile = "TMOOMST"
}