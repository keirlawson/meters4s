package com.ovoenergy.meters4s

import cats.effect.{Async, Sync}
import cats.implicits._
import cats.effect.implicits._
import cats.effect.std.Semaphore
import Reporter.{Counter, DistributionSummary, Gauge, Timer}
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.{MeterRegistry, Tag}
import io.micrometer.core.{instrument => micrometer}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicInteger

/**
  * Specify generic configuration accross a series of metrics
  *
  * @constructor create a new configuration instance
  * @param prefix a common prefix to be applied to all associated metric names
  * @param tags common tags to be applied to all associated metrics
  */
case class MetricsConfig(
    prefix: String = "",
    tags: Map[String, String] = Map.empty
)

/**
  * Represents an ongoing recording of a timing
  */
trait Sample[F[_]] {

  /**
    * Stop the timing and record its value against the Timer that created the Sample
    *
    * @return the timing in nanoseconds
    */
  def stop: F[Long]
}

/**
  * A generic metrics reporter
  */
trait Reporter[F[_]] {

  /**
    * Create a counter
    *
    * @param name the counter's name
    * @param tags tags associated with this counter
    * @return an effect that evaluates to a counter instance
    */
  def counter(
      name: String,
      tags: Map[String, String] = Map.empty
  ): F[Counter[F]]

  /**
    * Create a timer
    *
    * @param name the timer's name
    * @param tags tags associated with this timer
    * @return an effect that evaluates to a timer instance
    */
  def timer(
      name: String,
      tags: Map[String, String] = Map.empty,
      percentiles: Set[Double] = Set.empty
  ): F[Timer[F]]

  /**
    * Create a gauge
    *
    * @param name the gauge's name
    * @param tags tags associated with this gauge
    * @return an effect that evaluates to a gauge instance
    */
  def gauge(
      name: String,
      tags: Map[String, String] = Map.empty,
      initialValue: Int = 0
  ): F[Gauge[F]]

  /**
    * Create a distribution summary to track the sample distribution of events.
    * An example would be the response sizes for requests hitting an http server.
    *
    * @param name the summary's name
    * @param tags tags associated with this summary
    * @param percentiles Produces an additional time series for each requested percentile. This
    * percentile is computed locally, and so can't be aggregated with percentiles
    * computed across other dimensions (e.g. in a different instance).
    * @param baseUnit Base unit of the eventual distribution summary (eg. bytes).
    * @return an effect that evaluates to a distribution summary instance
    */
  def summary(
      name: String,
      tags: Map[String, String] = Map.empty,
      percentiles: Set[Double] = Set.empty,
      baseUnit: Option[String] = None
  ): F[DistributionSummary[F]]
}

object Reporter {

  /**
    * A generic event counter
    */
  trait Counter[F[_]] {

    /**
      * Increment this counter by 1
      */
    def increment: F[Unit] = this.incrementN(1)

    /**
      * Increment this counter be the specified amount
      *
      * @param amount the amount to increment the counter by
      */
    def incrementN(amount: Double): F[Unit]

    /**
      * Get the current value of the counter
      *
      * @return an effect evaluating to the current value of the counter
      */
    def count: F[Double]
  }

  /**
    * A generic timer for measuring short duration event latencies
    */
  trait Timer[F[_]] {

    /**
      * Record a timing against this timer
      *
      * @param d the amount of time to record
      */
    def record(d: FiniteDuration): F[Unit]

    /**
      * Start recording a timing
      *
      * @return an effect evaluating to a Sample instance that can be used to stop recording
      */
    def start: F[Sample[F]]

    /**
      * Wrap an effect evaluation in a timer, timing the latency of evaluating the resulting effect
      *
      * @param f an effect to be timed
      * @return a timer-wrapped effect
      */
    def wrap[A](f: F[A]): F[A]

    /**
      * Get the current event count for this timer
      *
      * @return an effect evaluating to the current event count of the timer
      */
    def count: F[Long]
  }

  /**
    * A generic gauge for measuring and reporting a fluctuating property
    */
  trait Gauge[F[_]] {

    def modify(f: Int => Int): F[Unit]

    /**
      * Set this gauge value
      *
      */
    def set(value: Int): F[Unit] = modify(_ => value)

    /**
      * Increment this gauge by 1
      *
      */
    def increment: F[Unit] = modify(_ + 1)

    /**
      * Increment this gauge by the specified amount
      *
      * @param n the amount to increment the gauge by
      */
    def incrementN(n: Int): F[Unit] = modify(_ + n)

    /**
      * Decrement this gauge by 1
      */
    def decrement: F[Unit] = modify(_ - 1)

    /**
      * Deccrement this gauge by the specified amount
      *
      * @param n the amount to decrement the gauge by
      */
    def decrementN(n: Int): F[Unit] = modify(_ - n)

    /** Run `action` with the gauge incremented before execution and decremented after termination (including error or cancelation) */
    def surround[A](action: F[A]): F[A]
  }

  /**
    * A generic distribution summary to track the sample distribution of events.
    */
  trait DistributionSummary[F[_]] {

    /**
      * Updates the statistics kept by the summary with the specified amount.
      * For example, the size in bytes of responses from a server.
      * If the amount is less than 0 the value will be dropped.
      *
      * @param value Amount for the event being measured.
      */
    def record(value: Double): F[Unit]

    /**
      * The number of times that record has been called since this summary was created.
      *
      * @return an effect evaluating to the current event count of the summary
      */
    def count: F[Long]
  }

  def apply[F[_]](implicit ev: Reporter[F]): Reporter[F] = ev

  /**
    * Create a Reporter wrapping a Micrometer SimpleMeterRegistry
    *
    * @param c configuration for this reporter
    * @return an effect evaluating to an instance of the wrapping reporter
    */
  def createSimple[F[_]: Async](
      c: MetricsConfig
  ): F[Reporter[F]] = {
    fromRegistry[F](new SimpleMeterRegistry, c)
  }

  /**
    * Create a Reporter wrapping a supplied Micrometer registry
    *
    * @param mx a Micrometer registry
    * @param config configuration for this reporter
    * @return an effect evaluating to an instance of the wrapping reporter
    */
  def fromRegistry[F[_]: Async](
      mx: MeterRegistry,
      config: MetricsConfig = MetricsConfig()
  ): F[Reporter[F]] =
    Semaphore[F](1).map { sem =>
      new MeterRegistryReporter[F](mx, config, mutable.Map.empty, sem)
    }

}

private class GaugeKey(
    private val name: String,
    tags: java.lang.Iterable[Tag]
) {
  private val tagSet: Set[micrometer.Tag] = tags.asScala.toSet

  override def equals(obj: Any): Boolean = obj match {
    case other: GaugeKey =>
      name == other.name &&
        tagSet == other.tagSet
    case _ => false
  }

  override def hashCode(): Int =
    name.hashCode * 31 + tagSet.hashCode()

  override def toString: String = s"GaugeKey($name, $tags)"
}

private class MeterRegistryReporter[F[_]](
    mx: MeterRegistry,
    config: MetricsConfig,
    activeGauges: mutable.Map[GaugeKey, AtomicInteger],
    gaugeSem: Semaphore[F]
)(
    implicit F: Sync[F]
) extends Reporter[F] {
  // local tags overwrite global tags
  def effectiveTags(tags: Map[String, String]) =
    (config.tags ++ tags).map { case (k, v) => Tag.of(k, v) }.asJava

  def metricName(name: String): String = s"${config.prefix}${name}"

  def counter(name: String, tags: Map[String, String]): F[Counter[F]] =
    F.blocking {
        micrometer.Counter
          .builder(metricName(name))
          .tags(effectiveTags(tags))
          .register(mx)
      }
      .map { c =>
        new Counter[F] {
          def incrementN(amount: Double) = F.delay(c.increment(amount))

          def count: F[Double] = F.delay(c.count())
        }
      }

  def timer(
      name: String,
      tags: Map[String, String],
      percentiles: Set[Double]
  ): F[Timer[F]] =
    F.blocking {
        micrometer.Timer
          .builder(metricName(name))
          .tags(effectiveTags(tags))
          .publishPercentiles(percentiles.toSeq: _*)
          .register(mx)
      }
      .map { t =>
        new Timer[F] {
          def record(d: FiniteDuration) =
            F.delay(t.record(d.toMillis, MILLISECONDS))

          def start = F.delay {
            val internal = micrometer.Timer.start(mx)
            new Sample[F] {
              def stop: F[Long] = F.delay(internal.stop(t))
            }
          }

          def wrap[A](f: F[A]): F[A] =
            for {
              sample <- start
              a <- f
              _ <- sample.stop
            } yield a

          def count: F[Long] = F.delay(t.count())
        }
      }

  private def registerGauge(
      name: String,
      tags: java.lang.Iterable[Tag],
      initialValue: Int
  ): F[AtomicInteger] = F.delay(new AtomicInteger(initialValue)).flatTap {
    gauge =>
      F.blocking(
        micrometer.Gauge
          .builder(
            name,
            gauge, { (i: AtomicInteger) => i.doubleValue }
          )
          .tags(tags)
          .register(mx)
      )
  }

  def gauge(
      name: String,
      tags: Map[String, String],
      initialValue: Int
  ): F[Gauge[F]] = {
    val pname = metricName(name)
    val allTags = effectiveTags(tags)
    val gaugeKey = new GaugeKey(pname, allTags)

    val gaugeValue: F[AtomicInteger] = gaugeSem.permit.use(_ =>
      activeGauges
        .get(gaugeKey)
        .fold {
          registerGauge(pname, allTags, initialValue)
            .flatTap(g => F.delay(activeGauges.put(gaugeKey, g)))
        }(_.pure[F])
    )

    gaugeValue.map { counter =>
      new Gauge[F] {

        def modify(f: Int => Int): F[Unit] =
          F.delay {
            counter.getAndUpdate(x => f(x))
            ()
          }

        def surround[A](action: F[A]): F[A] =
          increment.bracket(_ => action)(_ => decrement)
      }
    }

  }

  def summary(
      name: String,
      tags: Map[String, String],
      percentiles: Set[Double],
      baseUnit: Option[String]
  ): F[DistributionSummary[F]] =
    F.blocking {
        val builder = micrometer.DistributionSummary
          .builder(metricName(name))
          .tags(effectiveTags(tags))
          .publishPercentiles(percentiles.toSeq: _*)

        baseUnit.fold(builder)(builder.baseUnit).register(mx)
      }
      .map { d =>
        new DistributionSummary[F] {
          def record(value: Double): F[Unit] = F.delay(d.record(value))

          def count: F[Long] = F.delay(d.count())
        }
      }
}
