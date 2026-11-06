/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.httpapi

import java.util.UUID

import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol.DeliverySpecification
import spray.json._

/**
  * JSON protocol used by the http API.
  * Contains specific protocol messages and the JSON serialization instructions for the Spray JSON format
  * for all classes used with the http API.
  */
object HttpJsonProtocol extends DefaultJsonProtocol {
  import io.cafienne.bounded.cargosample.domain.CargoDomainJsonProtocol._

  case class PlanCargo(trackingId: UUID, deliverySpecification: DeliverySpecification)

  implicit val planCargoFmt: RootJsonFormat[PlanCargo] = jsonFormat2(PlanCargo.apply)

  case class ErrorResponse(msg: String)

  implicit val errorResponsFmt: RootJsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse.apply)

}
