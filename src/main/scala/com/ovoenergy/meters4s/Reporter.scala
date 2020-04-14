package com.ovoenergy.meters4s

import cats.effect.Sync
import cats.implicits._
import com.ovoenergy.meters4s.Reporter.{Counter, Timer}
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.{MeterRegistry, Tag}
import io.micrometer.core.{instrument => micrometer}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

case class MetricsConfig(
  prefix: String = "",
  tags: Map[String, String] = Map.empty,
)

trait Reporter[F[_]] {
  def counter(name: String, tags: Map[String, String] = Map.empty): F[Counter[F]]
  def timer(name: String, tags: Map[String, String] = Map.empty): F[Timer[F]]
}

object Reporter {
  trait Counter[F[_]] {
    def increment: F[Unit]
  }

  trait Timer[F[_]] {
    def record(d: FiniteDuration): F[Unit]
  }

  def apply[F[_]](implicit ev: Reporter[F]): Reporter[F] = ev

  def createSimple[F[_]](c: MetricsConfig)(implicit F: Sync[F]): Reporter[F] = {
    fromRegistry[F](new SimpleMeterRegistry, c)
  }

  def fromRegistry[F[_]](mx: MeterRegistry, config: MetricsConfig)(
      implicit F: Sync[F]
  ): Reporter[F] =
    new Reporter[F] {
      // local tags overwrite global tags
      def effectiveTags(tags: Map[String, String]) =
        (config.tags ++ tags).map { case (k, v) => Tag.of(k, v) }.asJava

      def counter(name: String, tags: Map[String, String]): F[Counter[F]] =
        F.delay {
            micrometer.Counter
              .builder(s"${config.prefix}${name}")
              .tags(effectiveTags(tags))
              .register(mx)
          }
          .map { c =>
            new Counter[F] {
              def increment = F.delay(c.increment)
            }
          }

      def timer(name: String, tags: Map[String, String]): F[Timer[F]] =
        F.delay {
            micrometer.Timer
              .builder(s"${config.prefix}${name}")
              .tags(effectiveTags(tags))
              .register(mx)
          }
          .map { t =>
            new Timer[F] {
              def record(d: FiniteDuration) = F.delay(t.record(d.toMillis, MILLISECONDS))
            }
          }

    }
}