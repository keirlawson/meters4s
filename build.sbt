lazy val root = (project in file("."))
  .settings(
    name := "meters4s",
    commonSettings,
    libraryDependencies ++= commonDependencies,
    git.remoteRepo := "git@github.com:ovotech/meters4s.git"
  )
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .aggregate(core, datadog, statsd, docs)

lazy val commonSettings = Seq(
  organization := "com.ovoenergy",
  version := "0.3.0",
  scalaVersion := "2.13.1",
  bintrayOrganization := Some("ovotech"),
  bintrayRepository := "maven-private",
  bintrayOmitLicense := true,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

lazy val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % "2.1.1",
  "org.typelevel" %% "cats-effect" % "2.1.2",
  "org.specs2" %% "specs2-core" % "4.8.3" % "test",
  "io.micrometer" % "micrometer-core" % "1.4.1",
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
    )
  )
  .dependsOn(datadog)
  .enablePlugins(MdocPlugin)
