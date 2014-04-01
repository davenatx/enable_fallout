import Dependencies._

import AssemblyKeys._

organization := "com.austindata"

name := "enabled_fallout"

version := "M2"

scalaVersion := "2.10.4"

scalacOptions ++= Seq("-optimize", "-deprecation", "-feature")

libraryDependencies ++= enable_fallout_dependencies

git.baseVersion := "1.0"

//versionWithGit

showCurrentGitBranch

scalariformSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

assemblySettings

mainClass in assembly := Some("com.austindata.orders.Driver")

jarName in assembly := name.value + "_" + scalaVersion.value + "-" + version.value +  ".jar"

resolvers += "Github Repo" at "http://davenatx.github.io/maven"

parallelExecution in Test := false