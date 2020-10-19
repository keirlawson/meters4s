lazy val additionalSupportedScalaVersions = List("2.12.11")

ThisBuild / dynverVTagPrefix := false
ThisBuild / bintrayOrganization := Some("ovotech")
ThisBuild / bintrayRepository := "maven"
ThisBuild / bintrayPackageLabels := Seq(
  "cats-effect",
  "micrometer",
  "metrics",
  "scala",
)
ThisBuild / releaseEarlyWith := BintrayPublisher
ThisBuild / releaseEarlyNoGpg := true
ThisBuild / releaseEarlyEnableSyncToMaven := false

lazy val root = (project in file("."))
  .settings(
    name := "meters4s",
    commonSettings,
    libraryDependencies ++= commonDependencies,
    git.remoteRepo := "git@github.com:ovotech/meters4s.git",
    siteSubdirName in ScalaUnidoc := "latest/api",
    addMappingsToSiteDir(
      mappings in (ScalaUnidoc, packageDoc),
      siteSubdirName in ScalaUnidoc
    ),
    crossScalaVersions := Nil,
    publish / skip := true,
    publishArtifact := false
  )
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(core, datadog, statsd, docs)

lazy val commonSettings = Seq(
  organization := "com.ovoenergy",
  scalaVersion := "2.13.1",
  crossScalaVersions ++= additionalSupportedScalaVersions,
  organizationName := "OVO Energy",
  organizationHomepage := Some(url("https://www.ovoenergy.com/")),
  homepage := Some(url("https://github.com/ovotech/meters4s")),
  startYear := Some(2020),
  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/ovotech/meters4s"),
      "git@github.com:ovotech/meters4s.git"
    )
  ),  
  developers := List(
    Developer(
      "keirlawson",
      "Keir Lawson",
      "keir,lawson@ovoenergy.com",
      url("https://github.com/keirlawson")
    )
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

lazy val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.1.1",
  "org.typelevel" %% "cats-effect" % "2.1.2",
  "org.specs2" %% "specs2-core" % "4.8.3" % "test",
  "io.micrometer" % "micrometer-core" % "1.4.1",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
  // See https://github.com/micrometer-metrics/micrometer/issues/1133#issuecomment-452434205
  "com.google.code.findbugs" % "jsr305" % "3.0.2" % Optional
)

lazy val core = project
  .settings(
    name := "meters4s",
    commonSettings,
    libraryDependencies ++= commonDependencies
  )

lazy val datadog = project
  .settings(
    name := "meters4s-datadog",
    commonSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.micrometer" % "micrometer-registry-datadog" % "1.4.1"
    )
  )
  .dependsOn(core)

lazy val statsd = project
  .settings(
    name := "meters4s-statsd",
    commonSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.micrometer" % "micrometer-registry-statsd" % "1.4.1"
    )
  )
  .dependsOn(core)

lazy val docs = project
  .settings(
    commonSettings,
    mdocIn := file("docs/README.md"),
    mdocOut := file("README.md"),
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    publish / skip := true,
    publishArtifact := false
  )
  .dependsOn(datadog)
  .enablePlugins(MdocPlugin)
