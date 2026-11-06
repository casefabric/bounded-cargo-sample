name := "cargo-sample"

organization := "io.cafienne"

description := "This is a sample application that makes use of the bounded framework for DDD, CQRS and Event Sourcing"

version := "0.2"

scalaVersion := "3.3.9"

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked"
)

scalafmtConfig := (ThisBuild / baseDirectory).value / "project/.scalafmt.conf"

scalafmtOnCompile := true

startYear := Some(2019)

licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))

headerLicense := {
  val toYear = java.time.Year.now
  Some(
    HeaderLicense.Custom(
      s"Copyright (C) 2018-$toYear  Cafienne B.V."
    )
  )
}

val pekkoVersion     = "1.7.1"
val pekkoHttpVersion = "1.4.2"
val boundedVersion   = "2.0.0"

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor"                 % pekkoVersion,
  "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream"                % pekkoVersion,
  "org.apache.pekko" %% "pekko-persistence"           % pekkoVersion,
  "org.apache.pekko" %% "pekko-persistence-query"     % pekkoVersion,
  "org.apache.pekko" %% "pekko-slf4j"                 % pekkoVersion,
  "org.apache.pekko" %% "pekko-http"                  % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-spray-json"       % pekkoHttpVersion,
  "io.spray"         %% "spray-json"                  % "1.3.6",
  // the LevelDB journal (pekko.persistence.journal.leveldb) used by application.conf
  "org.iq80.leveldb"          % "leveldb"        % "0.12",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "io.cafienne.bounded" %% "bounded-core" % boundedVersion,
  "io.cafienne.bounded" %% "bounded-test" % boundedVersion % Test,
  "org.lmdbjava"                % "lmdbjava"        % "0.9.1", // the cargo view store (and bounded's LMDB offset store)
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.6",
  "ch.qos.logback"              % "logback-classic" % "1.6.6",
  // Swagger (OpenAPI 3) documentation of the routes; the annotations are the Jakarta REST ones
  "com.github.swagger-akka-http" %% "swagger-pekko-http" % "2.16.0",
  "jakarta.ws.rs"                 % "jakarta.ws.rs-api"  % "3.0.0",
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion     % Test,
  "org.apache.pekko" %% "pekko-stream-testkit"      % pekkoVersion     % Test,
  "org.apache.pekko" %% "pekko-http-testkit"        % pekkoHttpVersion % Test,
  "org.apache.pekko" %% "pekko-persistence-testkit" % pekkoVersion     % Test,
  "org.scalatest"    %% "scalatest"                 % "3.2.20"         % Test
)

// bounded-core brings pekko-persistence-jdbc, built against Slick 3.5, next to Slick 3.6. Slick follows PVP, so sbt
// would otherwise fail the build on that eviction; 3.6 is a drop-in for the API pekko-persistence-jdbc uses.
libraryDependencySchemes ++= Seq(
  "com.typesafe.slick" %% "slick"          % VersionScheme.Always,
  "com.typesafe.slick" %% "slick-hikaricp" % VersionScheme.Always
)

enablePlugins(AutomateHeaderPlugin)
enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)

Compile / mainClass := Some("io.cafienne.bounded.cargosample.Boot")

dockerExposedPorts := Seq(8100)

dockerBaseImage := "eclipse-temurin:21-jre-alpine"

Docker / maintainer := """Cafienne <info@cafienne.io>"""

Docker / defaultLinuxInstallLocation := "/opt/cargo"

Docker / bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=$${app_home}/../conf/logback.xml""""

Docker / bashScriptExtraDefines += s"""addJava "-Dconfig.file=$${app_home}/../conf/local.conf""""

// lmdbjava needs these opens on Java 17+, so run and test in a forked JVM that has them
val lmdbJavaOptions = Seq(
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)
run / fork := true
run / javaOptions ++= lmdbJavaOptions
Test / fork := true
Test / javaOptions ++= lmdbJavaOptions
Docker / bashScriptExtraDefines ++= lmdbJavaOptions.map(opt => s"""addJava "$opt"""")
