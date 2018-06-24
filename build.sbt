name := "fijnstof"
version := "1.0"
organization := "nl.kransen"
maintainer := "Jeroen Kransen <jeroen@kransen.nl>"
packageSummary := "SDS021 sensor reader for Domoticz"
packageDescription := """This software reads sensors and pushes readings to external services.
  Supported sensors: SDS011, SDS021.
  Supported services: Domoticz, Luftdaten."""
scalaVersion := "2.12.4"

val circeVersion = "0.9.3"

libraryDependencies ++= Seq(
  "org.typelevel"          %% "cats-core"         % "1.0.1",
  "com.github.purejavacomm" % "purejavacomm"      % "1.0.1.RELEASE",
  "org.slf4j"               % "slf4j-log4j12"     % "1.7.25",
  "com.typesafe"            % "config"            % "1.3.2",
  "com.typesafe.akka"      %% "akka-http"         % "10.1.3",
  "com.typesafe.akka"      %% "akka-stream"       % "2.5.13",
  "io.circe"               %% "circe-core"        % circeVersion,
  "io.circe"               %% "circe-generic"     % circeVersion,
  "io.circe"               %% "circe-parser"      % circeVersion,

  "com.typesafe.akka"      %% "akka-http-testkit" % "10.1.1"          % Test,
  "org.scalatest"          %% "scalatest"         % "3.0.5"           % Test,
  "org.scalamock"          %% "scalamock"         % "4.1.0"           % Test
)

enablePlugins(JavaServerAppPackaging)
enablePlugins(SystemdPlugin)

//debianPackageDependencies in Debian ++= Seq("java8-runtime")

mappings in Universal += {
  // we are using the reference.conf as default application.conf
  // the user can override settings here
  val conf = (resourceDirectory in Compile).value / "reference.conf"
  // val conf = (resourceDirectory in Compile).value / "application.conf"
  conf -> "conf/application.conf"
}

daemonGroup in Linux := "dialout"

