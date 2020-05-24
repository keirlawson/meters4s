package com.ovoenergy.meters4s

import cats.effect.{Sync, Concurrent}
import cats.implicits._
import cats.effect.implicits._
import cats.effect.concurrent.Semaphore
import Reporter.{Counter, Timer, Gauge}
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.{MeterRegistry, Tag}
import io.micrometer.core.{instrument => micrometer}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicInteger

case class MetricsConfig(
    prefix: String = "",
    tags: Map[String, String] = Map.empty
)

trait Reporter[F[_]] {
  def counter(
      name: String,
      tags: Map[String, String] = Map.empty
  ): F[Counter[F]]
  def timer(name: String, tags: Map[String, String] = Map.empty): F[Timer[F]]
  def gauge(name: String, tags: Map[String, String] = Map.empty): F[Gauge[F]]
}

object Reporter {
  trait Counter[F[_]] {
    def increment: F[Unit] = this.increment(1)
    def increment(amount: Double): F[Unit]
    def count(): F[Double]
  }

  trait Timer[F[_]] {
    def record(d: FiniteDuration): F[Unit]
    def wrap[A](f: F[A]): F[A]
    def count(): F[Long]
  }

  trait Gauge[F[_]] {
    def increment: F[Unit] = incrementN(1)
    def incrementN(n: Int): F[Unit]

    def decrement: F[Unit] = incrementN(-1)
    def decrementN(n: Int): F[Unit] = incrementN(-n)

    /** Run `action` with the gauge incremented before execution and decremented after termination (including error or cancelation) */
    def surround[A](action: F[A]): F[A]
  }

  def apply[F[_]](implicit ev: Reporter[F]): Reporter[F] = ev

  def createSimple[F[_]: Concurrent](
      c: MetricsConfig
  ): F[Reporter[F]] = {
    fromRegistry[F](new SimpleMeterRegistry, c)
  }

  def fromRegistry[F[_]: Concurrent](
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
    F.delay {
        micrometer.Counter
          .builder(metricName(name))
          .tags(effectiveTags(tags))
          .register(mx)
      }
      .map { c =>
        new Counter[F] {
          def increment(amount: Double) = F.delay(c.increment(amount))

          def count(): F[Double] = F.delay(c.count())
        }
      }

  def timer(name: String, tags: Map[String, String]): F[Timer[F]] =
    F.delay {
        micrometer.Timer
          .builder(metricName(name))
          .tags(effectiveTags(tags))
          .register(mx)
      }
      .map { t =>
        new Timer[F] {
          def record(d: FiniteDuration) =
            F.delay(t.record(d.toMillis, MILLISECONDS))

          def wrap[A](f: F[A]): F[A] =
            for {
              sample <- F.delay(micrometer.Timer.start(mx))
              a <- f
              _ <- F.delay(sample.stop(t))
            } yield a

          def count(): F[Long] = F.delay(t.count())
        }
      }

  private def registerGauge(
      name: String,
      tags: java.lang.Iterable[Tag]
  ): F[AtomicInteger] = F.delay(new AtomicInteger()).flatTap { gauge =>
    F.delay(
      micrometer.Gauge
        .builder(
          name,
          gauge, { i: AtomicInteger => i.doubleValue }
        )
        .tags(tags)
        .register(mx)
    )
  }

  def gauge(name: String, tags: Map[String, String]): F[Gauge[F]] = {
    val pname = metricName(name)
    val allTags = effectiveTags(tags)
    val gaugeKey = new GaugeKey(pname, allTags)

    val gaugeValue: F[AtomicInteger] = gaugeSem.withPermit(
      activeGauges
        .get(gaugeKey)
        .fold {
          registerGauge(pname, allTags)
            .flatTap(g => F.delay(activeGauges.put(gaugeKey, g)))
        }(_.pure[F])
    )

    gaugeValue.map { counter =>
      new Gauge[F] {
        def incrementN(n: Int): F[Unit] =
          F.delay(counter.getAndAdd(n)).void

        def surround[A](action: F[A]): F[A] =
          increment.bracket(_ => action)(_ => decrement)
      }
    }

  }
}
