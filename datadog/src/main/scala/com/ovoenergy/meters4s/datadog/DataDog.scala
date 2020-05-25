package com.ovoenergy.meters4s.datadog

import cats.effect.{Resource, Sync}
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.datadog.DatadogMeterRegistry
import cats.implicits._

import scala.concurrent.duration._
import io.micrometer.datadog.{DatadogConfig => MmDatadogConfig}
import com.ovoenergy.meters4s.{MetricsConfig, Reporter}

import scala.concurrent.duration.FiniteDuration
import cats.effect.Concurrent

case class DataDogConfig(
    rate: FiniteDuration = 10.seconds,
    uri: String = "https://api.datadoghq.com",
    apiKey: String,
    applicationKey: Option[String] = None,
    descriptions: Boolean = false,
    hostTag: Option[String] = None
)

package object DataDog {
  private def createMeterRegistry[F[_]: Sync](
      c: DataDogConfig
  ): Resource[F, MeterRegistry] = {

    val datadogConfig: MmDatadogConfig = new MmDatadogConfig {
      override val apiKey = c.apiKey
      override val applicationKey = c.applicationKey.orNull
      override val enabled = true
      override val step = java.time.Duration.ofSeconds(c.rate.toSeconds.toLong)
      override val uri = c.uri
      override val descriptions = c.descriptions
      override val hostTag = c.hostTag.orNull
      // The parent of DatadogConfig need this abstract method to return null
      // to apply the default value
      def get(id: String): String = null
    }

    Resource
      .make(
        Sync[F].delay(
          DatadogMeterRegistry
            .builder(datadogConfig)
            .build
        )
      )(toStop => Sync[F].delay(toStop.stop))
      .widen[MeterRegistry]

  }

  def createReporter[F[_]: Concurrent](
      dataDogConfig: DataDogConfig,
      c: MetricsConfig
  ): Resource[F, Reporter[F]] = {
    val reg = createMeterRegistry[F](dataDogConfig)

    reg.flatMap(registry =>
      Resource.liftF(Reporter.fromRegistry[F](registry, c))
    )
  }
}
