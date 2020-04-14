lazy val root = (project in file("."))
  .settings(
    organization := "com.ovoenergy",
    name := "meters4s",
    version := "0.0.2",
    scalaVersion := "2.13.1",
    bintrayOrganization := Some("ovotech"),
    bintrayRepository := "maven-private",
    bintrayOmitLicense := true,
    libraryDependencies ++= Seq(
      "org.typelevel"            %% "cats-core"                   % "2.1.1",
      "org.typelevel"            %% "cats-effect"                 % "2.1.2",
      "org.specs2"               %% "specs2-core"                 % "4.8.3" % "test",
      "io.micrometer"            %  "micrometer-registry-datadog" % "1.4.1",
      // See https://github.com/micrometer-metrics/micrometer/issues/1133#issuecomment-452434205
      "com.google.code.findbugs" %  "jsr305"                      % "3.0.2" % Optional
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.0")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-Xfatal-warnings",
)
