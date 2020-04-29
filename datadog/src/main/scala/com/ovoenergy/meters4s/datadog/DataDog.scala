package com.ovoenergy.meters4s.datadog

import cats.effect.{Resource, Sync}
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.datadog.DatadogMeterRegistry
import cats.implicits._

import scala.concurrent.duration._
import io.micrometer.datadog.{DatadogConfig => MmDatadogConfig}
import com.ovoenergy.meters4s.{MetricsConfig, Reporter}

import scala.concurrent.duration.FiniteDuration

case class DataDogConfig(
    rate: FiniteDuration = 10.seconds,
    endpoint: String = "", //Uri = Uri(), FIXME used to be URI
    apiKey: String = "",
    applicationKey: String = ""
)

package object DataDog {
  private def createMeterRegistry[F[_]: Sync](
      c: DataDogConfig
  ): Resource[F, MeterRegistry] = {

    val datadogConfig: MmDatadogConfig = new MmDatadogConfig {
      override val apiKey = c.apiKey
      override val applicationKey = c.applicationKey
      override val enabled = true
      override val step = java.time.Duration.ofSeconds(c.rate.toSeconds.toLong)
      override val uri = c.endpoint.toString
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

  def createRegistry[F[_]: Sync](
      dataDogConfig: DataDogConfig,
      c: MetricsConfig
  ): Resource[F, Reporter[F]] = {
    createMeterRegistry[F](dataDogConfig).map(registry =>
      Reporter.fromRegistry(registry, c)
    )
  }
}
