package com.ovoenergy.meters4s

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import cats.effect.IO
import scala.jdk.CollectionConverters._
import io.micrometer.core.instrument.Tag
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.MockClock
import munit.CatsEffectSuite

class ReporterTest extends CatsEffectSuite {

  test("increment should increment underlying counter") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.counter("test.counter")
    testee.flatMap(_.increment).unsafeRunSync()

    assertEquals(registry.counter("test.counter").count(), 1d)
  }
  test("increment with amount must increment underlying counter by that amount") {
    val someAmount: Double = 123
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.counter("test.counter")
    testee.flatMap(_.incrementN(someAmount)).unsafeRunSync()

    assertEquals(registry.counter("test.counter").count(), someAmount)
  }
  test("count must return the value of the underlying counter") {
    val someAmount: Double = 123
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    registry.counter("test.counter").increment(someAmount)

    val testee = reporter.counter("test.counter")
    assertEquals(testee.flatMap(_.count).unsafeRunSync(), someAmount)
  }
  test("counter must add specified tags") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")

    val testee = reporter.counter("test.counter", someTags)
    testee.flatMap(_.increment).unsafeRunSync()

    val resultingTags = registry
      .find("test.counter")
      .counter()
      .getId
      .getTags
      .asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }
  test("counter must add configured global tags") {
    val registry = new SimpleMeterRegistry
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(tags = someTags))
        .unsafeRunSync()

    val testee = reporter.counter("test.counter")
    testee.flatMap(_.increment).unsafeRunSync()

    val resultingTags = registry
      .find("test.counter")
      .counter()
      .getId
      .getTags
      .asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }
  test("counter must add configured global prefix") {
    val registry = new SimpleMeterRegistry
    val somePrefix = "some.prefix"
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(prefix = somePrefix))
        .unsafeRunSync()

    val testee = reporter.counter("test.counter")
    testee.flatMap(_.increment).unsafeRunSync()

    val resultingName = registry
      .find(somePrefix + "test.counter")
      .counter()
      .getId
      .getName

    assert(resultingName.startsWith(somePrefix))
  }

  test("timer.record should record the supplied duration") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.timer("test.timer")
    testee
      .flatMap(_.record(FiniteDuration(10, TimeUnit.SECONDS)))
      .unsafeRunSync()

    assertEquals(registry.timer("test.timer").totalTime(TimeUnit.SECONDS), 10d)
  }

  test("timer.record should record the supplied percentiles") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val percentiles = Set(0.95d, 0.99d)
    val testee = reporter.timer("test.timer", percentiles = percentiles)
    testee
      .flatMap(_.record(FiniteDuration(10, TimeUnit.SECONDS)))
      .unsafeRunSync()

    assertEquals(
      registry
        .timer("test.timer")
        .takeSnapshot()
        .percentileValues()
        .map(_.percentile())
        .toSet,
      percentiles
    )
  }

  test("timer.wrap must time the wrapped task") {
    val mockClock = new MockClock
    val registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, mockClock)
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.timer("test.timer")

    testee
      .flatMap(_.wrap(IO { mockClock.add(123, TimeUnit.SECONDS) }))
      .unsafeRunSync()

    assertEquals(
      registry
        .timer("test.timer")
        .totalTime(TimeUnit.SECONDS),
      123d
    )
  }

  test("timer.count must return the value of the underlying counter") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    registry.timer("test.timer").record(10, TimeUnit.SECONDS)

    val testee = reporter.timer("test.timer")
    assertEquals(testee.flatMap(_.count).unsafeRunSync(), 1L)
  }

  test("timer must add specified tags") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")

    val testee = reporter.timer("test.timer", someTags)
    testee
      .flatMap(_.record(FiniteDuration(10, TimeUnit.SECONDS)))
      .unsafeRunSync()

    val resultingTags = registry
      .find("test.timer")
      .timer()
      .getId
      .getTags
      .asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }
  test("timer must add configured global tags") {
    val registry = new SimpleMeterRegistry
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(tags = someTags))
        .unsafeRunSync()

    val testee = reporter.timer("test.timer")
    testee
      .flatMap(_.record(FiniteDuration(10, TimeUnit.SECONDS)))
      .unsafeRunSync()

    val resultingTags = registry
      .find("test.timer")
      .timer()
      .getId
      .getTags
      .asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }
  test("timer must add configured global prefix") {
    val registry = new SimpleMeterRegistry
    val somePrefix = "some.prefix"
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(prefix = somePrefix))
        .unsafeRunSync()

    val testee = reporter.timer("test.timer")
    testee
      .flatMap(_.record(FiniteDuration(10, TimeUnit.SECONDS)))
      .unsafeRunSync()

    val resultingName = registry
      .find(somePrefix + "test.timer")
      .timer()
      .getId
      .getName

    assert(resultingName.startsWith(somePrefix))
  }

  test("set should set the gauge value") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.set(10)).unsafeRunSync()

    assertEquals(registry.find("test.gauge").gauge().value, 10d)
  }

  test("modify should modify the gauge value") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.gauge("test.gauge", initialValue = 8)
    testee.flatMap(_.modify(_ + 4)).unsafeRunSync()

    assertEquals(registry.find("test.gauge").gauge().value, 12d)
  }

  test("increment should increment the gauge") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.increment).unsafeRunSync()

    assertEquals(registry.find("test.gauge").gauge().value, 1d)
  }

  test("incrementN with amount must increment gauge by that amount") {
    val someAmount = 123
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.incrementN(someAmount)).unsafeRunSync()

    assertEquals(
      registry.find("test.gauge").gauge().value,
      someAmount.toDouble
    )
  }

  test("decrement should decrement the gauge") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.decrement).unsafeRunSync()

    assertEquals(registry.find("test.gauge").gauge().value, -1d)
  }

  test("decrementN with amount must decrement gauge by that amount") {
    val someAmount = 123
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.decrementN(someAmount)).unsafeRunSync()

    assertEquals(
      registry.find("test.gauge").gauge().value,
      -someAmount.toDouble
    )
  }

  test("must add specified tags") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")

    val testee = reporter.gauge("test.gauge", someTags)
    testee.flatMap(_.increment).unsafeRunSync()

    val resultingTags = registry
      .find("test.gauge")
      .gauge()
      .getId
      .getTags
      .asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }

  test("must add configured global tags") {
    val registry = new SimpleMeterRegistry
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(tags = someTags))
        .unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.increment).unsafeRunSync()

    val resultingTags = registry
      .find("test.gauge")
      .gauge()
      .getId
      .getTags
      .asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }

  test("must add configured global prefix") {
    val registry = new SimpleMeterRegistry
    val somePrefix = "some.prefix"
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(prefix = somePrefix))
        .unsafeRunSync()

    val testee = reporter.gauge("test.gauge")
    testee.flatMap(_.increment).unsafeRunSync()

    val resultingName = registry
      .find(somePrefix + "test.gauge")
      .gauge()
      .getId
      .getName

    assert(resultingName.startsWith(somePrefix))
  }

  test("summary.record should record the supplied amount") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val testee = reporter.summary("test.summary")
    testee
      .flatMap(_.record(10))
      .unsafeRunSync()

    assertEquals(registry.summary("test.summary").totalAmount(), 10d)
  }

  test("summary.record should record the supplied percentiles") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()

    val percentiles = Set(0.95d, 0.99d)
    val testee =
      reporter.summary("test.summary", percentiles = percentiles)
    testee
      .flatMap(_.record(10))
      .unsafeRunSync()

    assertEquals(
      registry
        .summary("test.summary")
        .takeSnapshot()
        .percentileValues()
        .map(_.percentile())
        .toSet,
      percentiles
    )
  }

  test("summary.count must return the value of the underlying counter") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    registry.summary("test.summary").record(10)

    val testee = reporter.summary("test.summary")
    assertEquals(testee.flatMap(_.count).unsafeRunSync(), 1L)
  }

  test("summary.record must add specified tags") {
    val registry = new SimpleMeterRegistry
    val reporter = Reporter.fromRegistry[IO](registry).unsafeRunSync()
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")

    val testee = reporter.summary("test.summary", someTags)
    testee
      .flatMap(_.record(10))
      .unsafeRunSync()

    val resultingTags =
      registry.find("test.summary").summary().getId.getTags.asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }
  test("summary.record must add configured global tags") {
    val registry = new SimpleMeterRegistry
    val someTags = Map("tag 1" -> "A", "tag 2" -> "B")
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(tags = someTags))
        .unsafeRunSync()

    val testee = reporter.summary("test.summary")
    testee
      .flatMap(_.record(10))
      .unsafeRunSync()

    val resultingTags =
      registry.find("test.summary").summary().getId.getTags.asScala

    assert(
      resultingTags.contains(Tag.of("tag 1", "A")) && resultingTags
        .contains(Tag.of("tag 2", "B"))
    )
  }
  test("summary.record must add configured global prefix") {
    val registry = new SimpleMeterRegistry
    val somePrefix = "some.prefix"
    val reporter =
      Reporter
        .fromRegistry[IO](registry, MetricsConfig(prefix = somePrefix))
        .unsafeRunSync()

    val testee = reporter.summary("test.summary")
    testee
      .flatMap(_.record(10))
      .unsafeRunSync()

    val resultingName =
      registry.find(somePrefix + "test.summary").summary().getId.getName

    assert(resultingName.startsWith(somePrefix))
  }

}
