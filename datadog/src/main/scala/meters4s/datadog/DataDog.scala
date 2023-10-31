package meters4s.datadog

import cats.effect.{Async, Resource, Sync}
import cats.implicits._
import meters4s.{MetricsConfig, Reporter}
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.datadog.{
  DatadogMeterRegistry,
  DatadogConfig => MmDatadogConfig
}

import scala.concurrent.duration._

/**
  * Configuration to be passed to the underlying Micrometer DatadogMeterRegistry
  *
  * @param rate how frequently to report metrics to Datadog
  * @param uri the Datadog server URI
  * @param apiKey the Datadog API key
  * @param applicationKey the Datadog application key
  * @param descriptions whether or not to send meter descriptions to Datadog
  * @param hostTag the tag that will be mapped to "host" when reporting metrics to datadog
  */
case class DataDogConfig(
    rate: FiniteDuration = 10.seconds,
    uri: String = "https://api.datadoghq.com",
    apiKey: String,
    applicationKey: Option[String] = None,
    descriptions: Boolean = false,
    hostTag: Option[String] = None,
    batchSize: Option[Int] = None
)

package object DataDog {

  /**
    * Create a Micrometer MeterRegistry
    *
    * @param c the configuration for reporting to Datadog
    * @return at datadog MeterRegistry
    */
  def createMeterRegistry[F[_]: Sync](
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
      override val batchSize = c.batchSize.getOrElse(10000)
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
      )(toClose => Sync[F].delay(toClose.close))
      .widen[MeterRegistry]

  }

  /**
    * Create a reporter for reporting metrics to Datadog
    *
    * @param dataDogConfig the configuration for reporting to Datadog
    * @param c the generic configuration to be applied to any produced metrics
    */
  def createReporter[F[_]: Async](
      dataDogConfig: DataDogConfig,
      c: MetricsConfig
  ): Resource[F, Reporter[F]] = {
    val reg = createMeterRegistry[F](dataDogConfig)

    reg.flatMap(registry =>
      Resource.eval(Reporter.fromRegistry[F](registry, c))
    )
  }
}
