package com.ovoenergy.meters4s.statsd

import cats.effect.{Resource, Sync, Concurrent}
import com.ovoenergy.meters4s.{MetricsConfig, Reporter}
import io.micrometer.statsd.{StatsdConfig => MmStatsdConfig}
import io.micrometer.core.instrument.MeterRegistry
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import cats.implicits._
import io.micrometer.statsd.StatsdFlavor
import io.micrometer.statsd.StatsdProtocol
import java.time.{Duration => JavaDuration}
import io.micrometer.statsd.StatsdMeterRegistry

case class StatsdConfig(
    rate: FiniteDuration = 10.seconds,
    flavor: StatsdFlavor = StatsdFlavor.DATADOG,
    buffered: Boolean = true,
    maxPacketLength: Int = 1400,
    pollingFrequency: FiniteDuration = 10.seconds,
    publishUnchangedMeters: Boolean = true,
    protocol: StatsdProtocol = StatsdProtocol.UDP,
    port: Int = 8125,
    host: String = "localhost"
)

package object StatsD {

  private def createMeterRegistry[F[_]: Sync](
      c: StatsdConfig
  ): Resource[F, MeterRegistry] = {

    val datadogConfig: MmStatsdConfig = new MmStatsdConfig {
      override val enabled = true
      override val step = JavaDuration.ofSeconds(c.rate.toSeconds.toLong)
      override val flavor = c.flavor
      override val buffered = c.buffered
      override val maxPacketLength = c.maxPacketLength
      override val pollingFrequency =
        JavaDuration.ofSeconds(c.pollingFrequency.toSeconds.toLong)
      override val publishUnchangedMeters = c.publishUnchangedMeters
      override val protocol = c.protocol
      override val port = c.port
      override val host = c.host

      // The parent of StatsdConfig need this abstract method to return null
      // to apply the default value
      def get(id: String): String = null
    }

    Resource
      .make(
        Sync[F].delay(
          StatsdMeterRegistry
            .builder(datadogConfig)
            .build
        )
      )(toStop => Sync[F].delay(toStop.stop))
      .widen[MeterRegistry]

  }

  def createRegistry[F[_]: Concurrent](
      config: StatsdConfig,
      c: MetricsConfig
  ): Resource[F, Reporter[F]] = {
    val reg = createMeterRegistry[F](config)
    
    reg.flatMap(registry =>
      Resource.liftF(Reporter.fromRegistry(registry, c))
    )
  }
}
