import ReleaseTransformations._

lazy val additionalSupportedScalaVersions = List("2.13.9", "2.12.17")

lazy val root = (project in file("."))
  .settings(
    name := "meters4s",
    commonSettings,
    libraryDependencies ++= commonDependencies,
    git.remoteRepo := "git@github.com:ovotech/meters4s.git",
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
  .aggregate(core, datadog, statsd, docs)

lazy val commonSettings = Seq(
  organization := "com.ovoenergy",
  scalaVersion := "3.1.3",
  crossScalaVersions ++= additionalSupportedScalaVersions,
  organizationName := "OVO Energy",
  organizationHomepage := Some(url("https://www.ovoenergy.com/")),
  homepage := Some(url("https://github.com/ovotech/meters4s")),
  startYear := Some(2020),
  licenses := Seq(
    ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
  ),
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
  )
)

lazy val publishSettings = Seq(
  publishTo := sonatypePublishToBundle.value,
  sonatypeProfileName := "com.ovoenergy",
  publishMavenStyle := true
)

lazy val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.8.0",
  "org.typelevel" %% "cats-effect" % "3.3.14",
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % "test",
  "io.micrometer" % "micrometer-core" % "1.9.4",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1",
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
      "io.micrometer" % "micrometer-registry-datadog" % "1.9.4"
    )
  )
  .dependsOn(core)

lazy val statsd = project
  .settings(
    name := "meters4s-statsd",
    commonSettings,
    publishSettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.micrometer" % "micrometer-registry-statsd" % "1.9.4"
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
