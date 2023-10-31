/*
 * Copyright 2020 OVO Energy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package meter4s.prometheus
import cats.effect.{Async, Resource, Sync}
import meters4s.{MetricsConfig, Reporter}
import io.micrometer.prometheus
import io.micrometer.prometheus.{
  PrometheusMeterRegistry,
  HistogramFlavor => PrometheusHistogramFlavor,
  PrometheusConfig => MmPrometheusConfig
}

import scala.concurrent.duration._

sealed trait HistogramFlavor { self =>
  def toJava: PrometheusHistogramFlavor = self match {
    case HistogramFlavor.Prometheus => PrometheusHistogramFlavor.Prometheus
    case HistogramFlavor.Victoria   => PrometheusHistogramFlavor.VictoriaMetrics
  }
}
object HistogramFlavor {
  case object Prometheus extends HistogramFlavor
  case object Victoria extends HistogramFlavor
}

/** Configuration to be passed to the underlying Micrometer
  * PrometheusMeterRegistry
  *
  * @param prefix
  *   the prefix to use for all metrics
  * @param step
  *   the step size to use in computing windowed statistics like max. To get the
  *   most out of this, align this with the scrape interval of your Prometheus
  *   instance.
  * @param descriptions
  *   whether or not to send meter descriptions to Prometheus (set to false to
  *   minimize the amount of data sent on each scrape)
  * @param histogramFlavor
  *   is the histogram flavor to use for the backing DistributionSummary and
  *   Timer
  */
final case class PrometheusConfig(
    prefix: String = "prometheus",
    step: FiniteDuration = 1.minute,
    descriptions: Boolean = false,
    histogramFlavor: HistogramFlavor = HistogramFlavor.Prometheus
)

object Prometheus {

  /** Create a Micrometer PrometheusMeterRegistry that your http endpoints will
    * use (i.e. call scrape on PrometheusMeterRegistry)
    * @param config
    * @tparam F
    * @return
    */
  def createMeterRegistry[F[_]: Sync](
      config: PrometheusConfig
  ): Resource[F, PrometheusMeterRegistry] = {
    val underlyingConfig: MmPrometheusConfig = new MmPrometheusConfig {
      // The parent of DatadogConfig need this abstract method to return null to apply the default value
      override def get(key: String): String = null
      override def prefix(): String = config.prefix
      override def descriptions(): Boolean = config.descriptions
      override def step(): java.time.Duration =
        java.time.Duration.ofSeconds(config.step.toSeconds.toLong)
      override def histogramFlavor(): prometheus.HistogramFlavor =
        config.histogramFlavor.toJava
    }

    val acquire = Sync[F].delay(new PrometheusMeterRegistry(underlyingConfig))
    val release = (r: PrometheusMeterRegistry) => Sync[F].delay(r.close())

    Resource.make(acquire)(release)
  }

  /** Create a Reporter that will be used to report metrics to Prometheus Meter
    * Registry
    * @param config
    *   is the configuration for the reporter
    * @param registry
    *   is the Prometheus Meter Registry
    * @tparam F
    * @return
    */
  def createReporter[F[_]: Async](
      config: MetricsConfig,
      registry: PrometheusMeterRegistry
  ): F[Reporter[F]] =
    Reporter.fromRegistry[F](registry, config)
}
