# Meters4s

Meters4s is a thin, functional wrapper around [Micrometer](https://micrometer.io/) designed to integrate
with the [Cats](https://typelevel.org/cats/) ecosystem. This allows for in-process metrics aggregation
for counters, timers, gauges and distributions.

## Install

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s" % "<see latest version>"
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
libraryDependencies += "com.ovoenergy" %% "meters4s-datadog" % "<see latest version>"
```

or

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s-statsd" % "<see latest version>"
```

or 

```scala
libraryDependencies += "com.ovoenergy" %% "meters4s-prometheus" % "<see latest version>"
```

## Usage

For comprehensive API documentation check [the scaladoc](https://ovotech.github.io/meters4s/latest/api/).

A simple usage example for incrementing a counter, backed by a Micrometer `SimpleMeterRegistry`:

```scala
import meters4s.{Reporter, MetricsConfig}
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
import meters4s.{MetricsConfig, Reporter}
import meters4s.datadog.{DataDog, DataDogConfig}
import cats.effect.IO

val datadog =
  DataDog.createReporter[IO](DataDogConfig(apiKey = "1234"), MetricsConfig())
datadog.use { reporter =>
  reporter.counter("my.counter").flatMap { counter => counter.increment }
}
```

## With Prometheus

Here is an example that reports CPU metrics as well as a counter that can be retrieved via the 
`PrometheusMeterRegistry`'s `scrape` method. Note your HTTP endpoint will expose this for Prometheus to scrape.

```scala
import cats.effect._
import cats.effect.std.Console
import cats.effect.syntax.all._
import cats.syntax.all._
import meter4s.prometheus._
import meters4s.{MetricsConfig, Reporter}
import io.micrometer.core.instrument.binder.system.ProcessorMetrics

import scala.concurrent.duration._

object PromExampleApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    program[IO](args).as(ExitCode.Success)

  def program[F[_]](args: List[String])(implicit F: Async[F], C: Console[F]): F[Nothing] = {
    val resources =
      for {
        registry <- Prometheus.createMeterRegistry[F](PrometheusConfig())
        _ <- Resource.eval(F.delay(new ProcessorMetrics().bindTo(registry)))
        reporter <- Resource.eval[F, Reporter[F]](
          Prometheus.createReporter[F](MetricsConfig(), registry)
        )
      } yield (registry, reporter)

    resources
      .evalMap { case (registry, reporter) =>
        reporter
          .counter("test_counter")
          .map(counter => (registry, reporter, counter))
      }
      .use { case (registry, reporter, counter) =>
        val scrape = C.println(registry.scrape)
        val increment = counter.increment

        (increment >> scrape).delayBy(10.seconds).foreverM
      }
  }
}
```

## HTTP4S

`meters4s-http4s` implements [http4s](https://http4s.org/) metrics.

This module records the following meters:

- Timer `default.response-time`
- Timer `default.response-headers-time`
- Gauge `default.active-requests`

The `default.response-time` timer has the `status-code`, `method` and `termination` tags.
The `default.response-headers-time` timer has the `method` tag.
The `default.active-requests` does not have any tag.

In addition to these tags, each metric will record the global tags set in the Config.

It is also possible to set a prefix for the metrics name using the `prefix` configuration setting.

The `default` name can be customised using a classifier function. With the same classifier function, it is possible to record additional tags using this syntax: `classifier[tag1:value1,tag2:value2,tag3:value3]`. The classifier part can be blank as well as the tags part can be empty.

The standard tags values are the following:

- statusCode
  - 2xx
  - 3xx
  - 4xx
  - 5xx

- method
  - head
  - get
  - put
  - patch
  - post
  - delete
  - options
  - move
  - trace
  - connect  
  - other

- termination
  - normal
  - abnormals
  - error
  - timeout


## Inspiration

This library was heavily inspired by (and in some places copied wholesale
from) [http4s-micrometer-metrics](https://github.com/ovotech/http4s-micrometer-metrics).