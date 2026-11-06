/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample

import com.typesafe.config.ConfigFactory

object SpecConfig {

  /*
   * Test configuration with the in-memory journal that has a query side (`inmemory-journal`, brought in by
   * bounded-core), which the projection tests need. Bounded tags every stored event with its aggregate type,
   * so no event adapter is configured.
   *
   * The aggregate root tests do not use this journal: TestableAggregateRoot starts its own actor system on the
   * Pekko persistence testkit journal.
   */
  val testConfig = ConfigFactory.parseString(
    """
      |      pekko {
      |        loglevel = "DEBUG"
      |        stdout-loglevel = "DEBUG"
      |        loggers = ["org.apache.pekko.testkit.TestEventListener"]
      |        actor {
      |           serialize-creators = off
      |           serialize-messages = off
      |           serialization-bindings {
      |             "io.cafienne.bounded.aggregate.DomainEvent" = jackson-json
      |             // enable below to check if all taskEvents have been serialized without java.io.Serializable
      |             "java.io.Serializable" = none
      |           }
      |        }
      |      persistence {
      |       publish-confirmations = on
      |       publish-plugin-commands = on
      |       journal {
      |          plugin = "inmemory-journal"
      |       }
      |       snapshot-store.plugin = "inmemory-snapshot-store"
      |      }
      |      test {
      |        single-expect-default = 10s
      |        timefactor = 1
      |      }
      |    }
      |    inmemory-read-journal {
      |      refresh-interval = "10ms"
      |      max-buffer-size = "1000"
      |    }
      |
      |    bounded.eventmaterializers.publish = true
      |
      |    bounded.eventmaterializers.offsetstore {
      |       type = "inmemory"
      |   }
    """.stripMargin
  )
}
