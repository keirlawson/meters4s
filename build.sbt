import ReleaseTransformations._

lazy val http4sVersion = "0.23.23"

lazy val additionalSupportedScalaVersions = List("2.13.12", "2.12.18")

lazy val root = (project in file("."))
  .settings(
    name := "meters4s",
    commonSettings,
    libraryDependencies ++= commonDependencies,
    git.remoteRepo := "git@github.com:keirlawson/meters4s.git",
    ScalaUnidoc / siteSubdirName := "latest/api",
    addMappingsToSiteDir(
      ScalaUnidoc / packageDoc / mappings,
      ScalaUnidoc / siteSubdirName
    ),
    crossScalaVersions := Nil,
    publish / skip := true,
    publishArtifact := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(core, datadog, statsd, prometheus, docs, http4s)

lazy val commonSettings = Seq(
  organization := "io.github.keirlawson",
  scalaVersion := "3.3.0",
  crossScalaVersions ++= additionalSupportedScalaVersions,
  homepage := Some(url("https://github.com/keirlawson/meters4s")),
  startYear := Some(2020),
  licenses := Seq(
    ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/keirlawson/meters4s"),
      "git@github.com:keirlawson/meters4s.git"
    )
  ),
  developers := List(
    Developer(
      "keirlawson",
      "Keir Lawson",
      "keirlawson@gmail.com",
      url("https://github.com/keirlawson")
    )
  )
)

lazy val publishSettings = Seq(
  publishTo := sonatypePublishToBundle.value,
  sonatypeProfileName := "io.github.keirlawson",
  publishMavenStyle := true
)

lazy val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.9.0",
  "org.typelevel" %% "cats-effect" % "3.5.0",
  "org.typelevel" %% "munit-cats-effect" % "2.0.0-M3" % Test,
  "io.micrometer" % "micrometer-core" % "1.10.5",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0",
  // See https://github.com/micrometer-metrics/micrometer/issues/1133#issuecomment-452434205
  "com.google.code.findbugs" % "jsr305" % "3.0.2" % Optional
)

lazy val core = project
  .settings(
    name := "meters4s",
    commonSettings,
    publishSettings,
    libraryDependencies ++= commonDependencies
  )

lazy val datadog = project
  .settings(
    name := "meters4s-datadog",
    commonSettings,
    publishSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.micrometer" % "micrometer-registry-datadog" % "1.10.5"
    )
  )
  .dependsOn(core)

lazy val statsd = project
  .settings(
    name := "meters4s-statsd",
    commonSettings,
    publishSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.micrometer" % "micrometer-registry-statsd" % "1.10.5"
    )
  )
  .dependsOn(core)

lazy val prometheus = project
  .settings(
    name := "meters4s-prometheus",
    commonSettings,
    publishSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.micrometer" % "micrometer-registry-prometheus" % "1.10.5"
    )
  )
  .dependsOn(core)

lazy val http4s = project
  .settings(
    name := "meters4s-http4s",
    commonSettings,
    publishSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "org.http4s" %% "http4s-core" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion % Test,
      "org.http4s" %% "http4s-server" % http4sVersion % Test,
      "org.http4s" %% "http4s-client" % http4sVersion % Test,
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
    publishArtifact := false,
    // mdoc (transitively) depends on scala-collection-compat_2.13,
    // which conflicts with core's dependency on scala-collection-compat_3
    libraryDependencies := libraryDependencies.value.map(
      _ excludeAll (
        ExclusionRule(
          organization = "org.scala-lang.modules",
          name = "scala-collection-compat_2.13"
        ),
      )
    )
  )
  .dependsOn(datadog)
  .enablePlugins(MdocPlugin)
