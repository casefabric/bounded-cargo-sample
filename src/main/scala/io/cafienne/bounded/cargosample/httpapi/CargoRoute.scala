/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.httpapi

import java.time.{OffsetDateTime, ZonedDateTime}
import jakarta.ws.rs.{Consumes, GET, POST, Path, Produces}
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{ArraySchema, Content, Schema}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse

import jakarta.ws.rs.core.MediaType
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.event.{Logging, LoggingAdapter}
import org.apache.pekko.http.scaladsl.server.{PathMatchers, Route}
import io.cafienne.bounded.aggregate._
import io.cafienne.bounded.cargosample.domain.{CargoCommandValidatorsImpl, CargoDomainProtocol}
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol.{
  CargoCommandMetaData,
  CargoId,
  CargoPlanned,
  TrackingId
}
import io.cafienne.bounded.cargosample.eventmaterializers.CargoQueries
import io.cafienne.bounded.cargosample.eventmaterializers.QueriesJsonProtocol.CargoViewItem
import io.swagger.v3.oas.annotations.enums.ParameterStyle
import io.swagger.v3.oas.annotations.parameters.RequestBody

import scala.util.{Failure, Success}

@Path("/")
class CargoRoute(commandGateway: CommandGateway, cargoQueries: CargoQueries)(implicit actorSystem: ActorSystem)
    extends CargoCommandValidatorsImpl(actorSystem)
    with SprayJsonSupport {

  import org.apache.pekko.http.scaladsl.server.Directives._
  import HttpJsonProtocol._
  import io.cafienne.bounded.cargosample.persistence.CargoDomainEventJsonProtocol._

  val logger: LoggingAdapter = Logging(actorSystem, getClass.getName)

  val routes: Route = { getCargo ~ planCargo }

  @GET
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(
    description = "Fetch the data of a cargo",
    operationId = "getcargo",
    parameters = Array(new Parameter(name = "cargoId", style = ParameterStyle.DEFAULT)),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "data of a single cargo",
        content =
          Array(new Content(array = new ArraySchema(schema = new Schema(implementation = classOf[CargoViewItem]))))
      ),
      new ApiResponse(responseCode = "204", description = "No content"),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  ) // , content = classOf[ErrorResponse])  )
  @Path("cargo/{cargoId}")
  private def getCargo =
    get {
      path("cargo" / PathMatchers.JavaUUID) { id =>
        val cargoId = CargoId(id)
        cargoQueries.getCargo(cargoId) match {
          case Some(cargoResponse) => complete(StatusCodes.OK -> cargoResponse)
          case None => complete(StatusCodes.NotFound -> ErrorResponse(s"Cargo with id $cargoId is not found"))
        }
      }
    }

  @POST
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Operation(
    description = "Plan a new Cargo",
    operationId = "plancargo",
    requestBody =
      new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[PlanCargo])))),
    responses = Array(
      new ApiResponse(
        responseCode = "201",
        description = "data of a newly created cargo",
        content = Array(new Content(schema = new Schema(implementation = classOf[CargoViewItem])))
      ),
      new ApiResponse(
        responseCode = "203",
        description = "Processing succeeded but API could not transform the response"
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  ) // , content = classOf[ErrorResponse])  )
  @Path("cargo")
  private def planCargo =
    post {
      path("cargo") {
        entity(as[PlanCargo]) { planCargo =>
          val metadata = CargoCommandMetaData(OffsetDateTime.now(), None)
          onComplete(
            commandGateway.sendAndAsk(
              CargoDomainProtocol.PlanCargo(
                metadata,
                CargoId(java.util.UUID.randomUUID()),
                TrackingId(planCargo.trackingId),
                planCargo.deliverySpecification
              )
            )
          ) {
            case Success(Ok(List(value: CargoPlanned, _*))) =>
              logger.debug("API received command reply {}", value)
              complete(StatusCodes.Created -> value)
            case Success(Ko(failure)) =>
              logger.warning("API received Ko {} for command: PlanCargo", failure)
              failure match {
                case f => complete(StatusCodes.InternalServerError -> ErrorResponse(f.toString))
              }
            case Success(other) =>
              logger.error("API received Success reply it does not understand: {}", other)
              complete(StatusCodes.NonAuthoritativeInformation -> other.toString)
            case Failure(err) =>
              logger.warning("API received a failed Future with {}", err)
              complete(
                StatusCodes.InternalServerError -> ErrorResponse(
                  err.toString + Option(err.getCause)
                    .map(t => s" due to ${t.getMessage}")
                    .getOrElse("")
                )
              )
          }
        }
      }
    }
}
