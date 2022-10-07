# Meters4s

Meters4s is a thin, functional wrapper around [Micrometer](https://micrometer.io/) designed to integrate
with the [Cats](https://typelevel.org/cats/) ecosystem. This allows for in-process metrics aggregation
for counters, timers, gauges and distributions.

## Install

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s" % "1.1.2"
```

Or for Cats Effect 2.x use the 0.4.x series.

You will likely also want to add the module corresponding to whichever monitoring system you want to report metrics to.
All systems supported by micrometer can be used by brining in the corresponding micrometer dependency and then using
`Reporter.fromRegistry` to construct a reporter.

For developer convenience we also provide a couple of modules for particular monitoring systems, specifically Datadog
and
StatsD to provide and ergonomic means for creating reporters for these registries. These can be added to your project as
follows:

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s-datadog" % "1.1.2"
```

or

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s-statsd" % "1.1.2"
```

## Usage

For comprehensive API documentation check [the scaladoc](https://ovotech.github.io/meters4s/latest/api/).

A simple usage example for incrementing a counter, backed by a Micrometer `SimpleMeterRegistry`:

```scala
import com.ovoenergy.meters4s.{Reporter, MetricsConfig}
import cats.effect.IO

val config = MetricsConfig()
for {
  reporter <- Reporter.createSimple[IO](config)
  counter <- reporter.counter("my.counter")
  _ <- counter.increment
} yield ()
```

### With Datadog

```scala
import com.ovoenergy.meters4s.{MetricsConfig, Reporter}
import com.ovoenergy.meters4s.datadog.{DataDog, DataDogConfig}
import cats.effect.IO

val datadog =
  DataDog.createReporter[IO](DataDogConfig(apiKey = "1234"), MetricsConfig())
datadog.use { reporter =>
  reporter.counter("my.counter").flatMap { counter => counter.increment }
}
```

## Inspiration

This library was heavily inspired by (and in some places copied wholesale
from) [http4s-micrometer-metrics](https://github.com/ovotech/http4s-micrometer-metrics).
