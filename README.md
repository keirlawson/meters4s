# Meters4s

Meters4s is a thin, functional wrapper around [Micrometer](https://micrometer.io/) designed to integrate 
with the [Cats](https://typelevel.org/cats/) ecosystem.  This allows for in-process metrics aggregation 
for counters, timers, gauges and distributions.

## Install

Presently this library resides in the ovotech `maven-private` bintray which can be added yo your build.sbt with

```scala
resolvers ++= Seq(
    Resolver.bintrayRepo("ovotech", "maven-private")
)
```

Note that this repository requires authentication.

Add the following depdency to your `build.sbt`:

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s" % "0.3.0"
```

You will likely also want to add the module corresponding to whichever monitoring system you want to report metrics to.  All 
systems supported by micrometer can be used by brining in the corresponding micormeter dependecy and then using 
`Reporter.fromRegistry` to construct a reporter.

For developer convience we also provide a couple of modules for particular monitoring systems, specifically Datadog and 
StatsD to provide and ergonomic means for creating reporters for these registries.  These can be added to your poject as follows:

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s-datadog" % "0.3.0"
```

or

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s-statsd" % "0.3.0"
```

## Usage

A simple usage example for incrementing a counter, backed by a Micrometer `SimpleMeterRegistry`:

```scala
import com.ovoenergy.meters4s.{Reporter, MetricsConfig}
import cats.effect.IO
import scala.concurrent.ExecutionContext.Implicits.global

implicit val cs = IO.contextShift(global)

val config = MetricsConfig()
for {
    reporter <- Reporter.createSimple[IO](config)
    counter  <- reporter.counter("my.counter")
    _        <- counter.increment
} yield ()
```

### With Datadog

```scala
import com.ovoenergy.meters4s.{MetricsConfig, Reporter}
import com.ovoenergy.meters4s.datadog.{DataDog, DataDogConfig}
import cats.effect.IO

val datadog = DataDog.createReporter[IO](DataDogConfig(apiKey = "1234"), MetricsConfig())
datadog.use { reporter =>
    reporter.counter("my.counter").flatMap { counter =>
        counter.increment
    }
}
```

## Inspiration

This library was heavily inspired by (and in some places copied wholesale from) [http4s-micrometer-metrics](https://github.com/ovotech/http4s-micrometer-metrics).