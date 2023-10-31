package meters4s.http4s

import scala.concurrent.duration._

import cats.effect._
import cats.syntax.all._

import org.http4s.metrics.TerminationType._
import org.http4s.metrics.{MetricsOps, TerminationType}
import org.http4s.{Method, Status}
import meters4s.Reporter

object Meters4s {

  private val TagsReg = """.*?\[([^\]]*)\]""".r
  private val TagReg = """([^:]*)\s*:\s*(.*)""".r

  def apply[F[_]: Async](
      reporter: Reporter[F],
      percentiles: Set[Double] = Set.empty
  ): MetricsOps[F] =
    new MetricsOps[F] {

      private def namespace(classifier: Option[String]): String = {
        classifier
          .map(_.takeWhile(_ != '[').trim)
          .filter(_.nonEmpty)
          .getOrElse("default")
      }

      private def name(classifier: Option[String], key: String): String =
        s"${namespace(classifier)}.$key"

      private def tags(classifier: Option[String]): Map[String, String] = {
        classifier
          .collect {
            case TagsReg(tagsString) if tagsString.trim.nonEmpty =>
              tagsString
                .split(",")
                .collect {
                  case TagReg(key, value) =>
                    Map(key -> value)
                }
                .reduce(_ ++ _)
          }
          .getOrElse(Map.empty)

      }

      def increaseActiveRequests(classifier: Option[String]): F[Unit] =
        reporter
          .gauge(name(classifier, "active-requests"), tags(classifier))
          .flatMap(_.increment)

      def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
        reporter
          .gauge(name(classifier, "active-requests"), tags(classifier))
          .flatMap(_.decrement)

      def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String]
      ): F[Unit] =
        reporter
          .timer(
            name(classifier, "response-headers-time"),
            tags(classifier) ++ methodTags(method),
            percentiles
          )
          .flatMap(_.record(elapsed.nanos))

      def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String]
      ): F[Unit] = {
        val terminationTags = terminationType match {
          case Abnormal(_) => "termination" -> "abnormal"
          case Error(_)    => "termination" -> "error"
          case Canceled    => "termination" -> "cancelled"
          case Timeout     => "termination" -> "timeout"
        }

        recordResponseTime(
          classifier,
          tags(classifier) ++ Map(terminationTags),
          elapsed
        )
      }
      def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]
      ): F[Unit] = {
        val statusTags = status.responseClass match {
          case Status.Informational => "status-code" -> "1xx"
          case Status.Successful    => "status-code" -> "2xx"
          case Status.Redirection   => "status-code" -> "3xx"
          case Status.ClientError   => "status-code" -> "4xx"
          case Status.ServerError   => "status-code" -> "5xx"
        }
        val allTags = tags(classifier) ++
          Map("termination" -> "normal", statusTags) ++
          methodTags(method)

        recordResponseTime(
          classifier,
          allTags,
          elapsed
        )
      }

      private def recordResponseTime(
          classifier: Option[String],
          tags: Map[String, String],
          elapsed: Long
      ): F[Unit] =
        reporter
          .timer(name(classifier, "response-time"), tags, percentiles)
          .flatMap(_.record(elapsed.nanos))

      private def methodTags(method: Method): Map[String, String] = Map(
        "method" -> method.name.toLowerCase
      )

    }
}
