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

package com.ovoenergy.meters4s

import scala.concurrent.duration._

import cats.FlatMap
import cats.syntax.all._

object syntax {

  /** Allow to call the {{Reporter.Timer}} methods on the {{F[Reporter.Timer]}}
    */
  implicit class FTimer[F[_]: FlatMap](ft: F[Reporter.Timer[F]])
      extends Reporter.Timer[F] {
    def count: F[Long] = ft.flatMap(_.count)
    def record(d: FiniteDuration): F[Unit] = ft.flatMap(_.record(d))
    def start: F[Sample[F]] = ft.flatMap(_.start)
    def wrap[A](f: F[A]): F[A] = ft.flatMap(_.wrap(f))
  }

  /** Allow to call the {{Reporter.Gauge}} methods on the {{F[Reporter.Gauge]}}
    */
  implicit class FGauge[F[_]: FlatMap](ft: F[Reporter.Gauge[F]])
      extends Reporter.Gauge[F] {
    def modify(f: Int => Int): F[Unit] = ft.flatMap(_.modify(f))
    def surround[A](action: F[A]): F[A] = ft.flatMap(_.surround(action))
  }

  /** Allow to call the {{Reporter.Counter}} methods on the
    * {{F[Reporter.Counter]}}
    */
  implicit class FCounter[F[_]: FlatMap](ft: F[Reporter.Counter[F]])
      extends Reporter.Counter[F] {
    def count: F[Double] = ft.flatMap(_.count)
    def incrementN(amount: Double): F[Unit] = ft.flatMap(_.incrementN(amount))
  }
}
