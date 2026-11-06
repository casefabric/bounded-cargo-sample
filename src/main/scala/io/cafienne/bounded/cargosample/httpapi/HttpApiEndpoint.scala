/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.httpapi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server._
import io.cafienne.bounded.aggregate.CommandGateway
import io.cafienne.bounded.cargosample.eventmaterializers.CargoQueries
import io.cafienne.bounded.cargosample.httpapi.HttpJsonProtocol.ErrorResponse
import io.cafienne.bounded.config.Configured
import spray.json.DeserializationException

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * This service wires all provided service routes together and ensures proper handling issues in the requests
  */
class HttpApiEndpoint(commandGateway: CommandGateway, cargoQueries: CargoQueries)(implicit system: ActorSystem)
    extends Configured
    with SprayJsonSupport {

  import Directives._
  import HttpJsonProtocol._

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("pekko.actor.default-dispatcher")

  val logger = Logging(system, classOf[HttpApiEndpoint])

  def log(e: Throwable)(handle: Route): Route =
    rc => {
      logger.error(s"Request ${rc.request} could not be handled normally. Cause: ${e.getMessage}", e)
      handle(rc)
    }

  def defaultExceptionHandler(e: Throwable): Route =
    log(e) {
      badRequest(
        e.getMessage + Option(e.getCause)
          .map(t => s" cause: ${t.getMessage}")
          .getOrElse("")
      )
    }

  def badRequest(msg: String): Route =
    rc => rc.complete(StatusCodes.BadRequest -> ErrorResponse(msg))

  def notAuthorizedHandler(e: Throwable): Route =
    log(e) { rc =>
      rc.complete(StatusCodes.Unauthorized -> ErrorResponse(e.getMessage))
    }

  implicit val requestServiceExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: DeserializationException    => defaultExceptionHandler(e)
    case e: java.lang.SecurityException => notAuthorizedHandler(e)
    case e: Exception                   => defaultExceptionHandler(e)
  }

  implicit def requestServiceRejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case MalformedRequestContentRejection(errorMessage, cause) =>
        complete(StatusCodes.BadRequest -> ErrorResponse(errorMessage))
      }
      .handle { case AuthorizationFailedRejection =>
        complete(StatusCodes.Forbidden -> ErrorResponse("Not Authorized"))
      }
      .handle { case ValidationRejection(msg, _) =>
        complete(StatusCodes.InternalServerError -> ErrorResponse("Internal error due to: " + msg))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(StatusCodes.MethodNotAllowed -> ErrorResponse(s"Can't do that! Supported: ${names mkString " or "}!"))
      }
      .handleNotFound {
        complete(StatusCodes.NotFound -> ErrorResponse("Not here!"))
      }
      .result()

  val swaggerService = new SwaggerHttpServiceRoute(system)
  val cargoRoute     = new CargoRoute(commandGateway, cargoQueries)

  val route: Route =
    handleExceptions(requestServiceExceptionHandler) {
      handleRejections(requestServiceRejectionHandler) {
        options {
          complete(StatusCodes.OK)
        } ~
          swaggerService.swaggerUIRoute ~
          cargoRoute.routes
      }
    }

  def runServer(host: String, port: Int): Unit = {
    val httpServer = Http().newServerAt(host, port).bind(route)
    httpServer onComplete {
      case Success(answer) => logger.info("service is available: " + answer)
      case Failure(msg)    => logger.error("service failed: " + msg)
    }
  }
}

object HttpApiEndpoint
