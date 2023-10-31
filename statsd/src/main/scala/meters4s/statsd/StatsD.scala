package meters4s.statsd

import cats.effect.{Resource, Sync, Async}
import meters4s.{MetricsConfig, Reporter}
import io.micrometer.statsd.{StatsdConfig => MmStatsdConfig}
import io.micrometer.core.instrument.MeterRegistry
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import cats.implicits._
import io.micrometer.statsd.StatsdFlavor
import io.micrometer.statsd.StatsdProtocol
import java.time.{Duration => JavaDuration}
import io.micrometer.statsd.StatsdMeterRegistry

/**
  * Configuration to be passed to the underlying Micrometer DatadogMeterRegistry
  *
  * @param rate how frequently to report metrics to StatsD
  * @param flavor the type of StatsD to talk to
  * @param buffered whether or not buffer metrics to before sending to the StatsD server
  * @param maxPacketLength the max length of the metrics payload
  * @param pollingFrequency how often gauges will be polled
  * @param publishUnchangedMeters whether unchanged meters should be published to the StatsD server
  * @param protocol the protocol of the connection to the StatsD agent
  * @param port the port of the StatsD agent
  * @param host the host name of the StatsD agent
  */
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

  /**
    * Create a Micrometer MeterRegistry
    *
    * @param c the configuration for reporting to StatsD
    * @return at statsD MeterRegistry
    */
  def createMeterRegistry[F[_]: Sync](
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
      )(toClose => Sync[F].delay(toClose.close))
      .widen[MeterRegistry]

  }

  /**
    * Create a reporter for reporting metrics to a StatsD instance
    *
    * @param config the configuration for reporting to StatsD
    * @param c the generic configuration to be applied to any produced metrics
    */
  def createReporter[F[_]: Async](
      config: StatsdConfig,
      c: MetricsConfig
  ): Resource[F, Reporter[F]] = {
    val reg = createMeterRegistry[F](config)

    reg.flatMap(registry => Resource.eval(Reporter.fromRegistry(registry, c)))
  }
}
