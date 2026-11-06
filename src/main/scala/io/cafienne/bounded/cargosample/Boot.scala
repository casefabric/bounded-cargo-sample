/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample

import java.io.File

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.{Logging, LoggingAdapter}
import org.apache.pekko.util.Timeout
import io.cafienne.bounded.aggregate.RouterCommandGateway
import io.cafienne.bounded.cargosample.domain.{CargoCreator, FixedLocationsProvider}
import io.cafienne.bounded.cargosample.eventmaterializers.{CargoLmdbClient, CargoQueriesImpl, CargoViewWriter}
import io.cafienne.bounded.cargosample.httpapi.HttpApiEndpoint
import io.cafienne.bounded.config.Configured
import io.cafienne.bounded.eventmaterializers._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Boot extends Configured {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("cargo-service")
    import system.dispatcher

    implicit val logger: LoggingAdapter = Logging(system, getClass.getName)

    val host = config.getString("application.bind.host")
    val port = config.getInt("application.bind.port")

    sys addShutdownHook {
      logger.info("Shutting down the Cargo Service")
      Await.ready(system.terminate(), 20.seconds).onComplete {
        case Success(s)   => logger.info(s"system terminated with $s")
        case Failure(msg) => logger.error(s"system shutdown failed due to $msg")
      }
    }

    val lmdbPath        = config.getString("application.lmdb-path")
    val cargoLmdbClient = new CargoLmdbClient(new File(lmdbPath, "cargo"))

    val cargoQueries = new CargoQueriesImpl(cargoLmdbClient)
    // ReadJournalOffsetStore keeps the offset of the view in the store that belongs to the configured journal
    val cargoViewProjectionWriter = new CargoViewWriter(system, cargoLmdbClient) with ReadJournalOffsetStore

    val eventMaterializers = new EventMaterializers(
      List(
        cargoViewProjectionWriter
      )
    )

    implicit val timeout: Timeout = Timeout(5.seconds)
    // The gateway starts an aggregate for a command when it is not running and stops it after the idle time
    val commandGateway =
      new RouterCommandGateway(system, 10.minutes, new CargoCreator(system, new FixedLocationsProvider()))

    val httpApiEndpoint = new HttpApiEndpoint(
      commandGateway,
      cargoQueries
    )

    try {
      // Startup event listeners. After replay start http server
      eventMaterializers.startUp(true).onComplete {
        case Success(_) =>
          logger.info(s"Start HTTP server")
          httpApiEndpoint.runServer(host, port)
        case Failure(msg) =>
          logger.error(
            "Could not start Cargo Service due to {}",
            msg.getMessage + Option(msg.getCause).fold("")(t => s" Cause: ${t.getMessage} ")
          )
      }
    } catch {
      case t: Throwable =>
        logger.error(s"Boot loop crashed with ${t.getMessage}, stacktrace: ")
        t.printStackTrace()
    }
  }

}
