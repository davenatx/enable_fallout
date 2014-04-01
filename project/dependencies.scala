import sbt._
import sbt.Keys._

object Dependencies {
 
  // Versions
  val jt400Version = "7.10"
  val scalaLoggingVersion = "1.0.1"
  val logbackVersion = "1.0.13"
  val configVersion = "1.0.2"
  
  // Libraries
  val jt400 =  "com.github.davenatx" % "jt400" % jt400Version
  val scalaLogging =  "com.typesafe" %% "scalalogging-slf4j" % scalaLoggingVersion
  val logback = "ch.qos.logback" % "logback-classic" % logbackVersion
  val config = "com.typesafe" % "config" % configVersion
  
  // Projects
  val enable_fallout_dependencies = Seq(jt400, scalaLogging, logback, config)

}
