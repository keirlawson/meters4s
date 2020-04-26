package com.ovoenergy.meters4s

import org.specs2.mutable.Specification
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import cats.effect.IO
import scala.jdk.CollectionConverters._
import io.micrometer.core.instrument.Tag

class ReporterTest extends Specification {
  "counter" >> {
    "increment should increment underlying counter" >> {
      val registry = new SimpleMeterRegistry
      val reporter = Reporter.fromRegistry[IO](registry)

      val testee = reporter.counter("test.counter")
      testee.flatMap(_.increment).unsafeRunSync()

      registry.counter("test.counter").count() must_== 1
    }
    "increment with amount must increment underlying counter by that amount" >> {
      val someAmount = 123
      val registry = new SimpleMeterRegistry
      val reporter = Reporter.fromRegistry[IO](registry)

      val testee = reporter.counter("test.counter")
      testee.flatMap(_.increment(someAmount)).unsafeRunSync()

      registry.counter("test.counter").count() must_== someAmount
    }
    "count must return the value of the underlying counter" >> {
      val someAmount = 123
      val registry = new SimpleMeterRegistry
      val reporter = Reporter.fromRegistry[IO](registry)
      registry.counter("test.counter").increment(someAmount)

      val testee = reporter.counter("test.counter")
      testee.flatMap(_.count()).unsafeRunSync() must_== someAmount
    }
    "must add specified tags" >> {
      val registry = new SimpleMeterRegistry
      val reporter = Reporter.fromRegistry[IO](registry)
      val someTags = Map("tag 1" -> "A", "tag 2" -> "B")

      val testee = reporter.counter("test.counter", someTags)
      testee.flatMap(_.increment).unsafeRunSync()

      val resultingTags = registry
        .find("test.counter")
        .counter()
        .getId()
        .getTags()
        .asScala

      resultingTags must contain(
        Tag.of("tag 1", "A"),
        Tag.of("tag 2", "B")
      )
    }
    "must add configured global tags" >> {
      val registry = new SimpleMeterRegistry
      val someTags = Map("tag 1" -> "A", "tag 2" -> "B")
      val reporter =
        Reporter.fromRegistry[IO](registry, MetricsConfig(tags = someTags))

      val testee = reporter.counter("test.counter")
      testee.flatMap(_.increment).unsafeRunSync()

      val resultingTags = registry
        .find("test.counter")
        .counter()
        .getId()
        .getTags()
        .asScala

      resultingTags must contain(
        Tag.of("tag 1", "A"),
        Tag.of("tag 2", "B")
      )
    }
    "must add configured global prefix" >> {
      val registry = new SimpleMeterRegistry
      val somePrefix = "some.prefix"
      val reporter =
        Reporter.fromRegistry[IO](registry, MetricsConfig(prefix = somePrefix))

      val testee = reporter.counter("test.counter")
      testee.flatMap(_.increment).unsafeRunSync()

      val resultingName = registry
        .find(somePrefix + "test.counter")
        .counter()
        .getId()
        .getName()

      resultingName must startWith(somePrefix)
    }
  }

  "timer" >> {
    "must " >> {
      2 must_== 2
    }
  }
}
