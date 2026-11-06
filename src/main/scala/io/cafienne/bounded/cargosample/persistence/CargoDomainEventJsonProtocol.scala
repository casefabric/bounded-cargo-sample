/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.persistence

import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol.{CargoPlanned, Loaded, NewDeliverySpecified, Unloaded}
import spray.json._

object CargoDomainEventJsonProtocol extends DefaultJsonProtocol {
  import io.cafienne.bounded.cargosample.domain.CargoDomainJsonProtocol._

  implicit val cargoPlannedFmt: RootJsonFormat[CargoPlanned]                 = jsonFormat4(CargoPlanned.apply)
  implicit val cargoLoadedFmt: RootJsonFormat[Loaded]                        = jsonFormat4(Loaded.apply)
  implicit val cargoUnloadedFmt: RootJsonFormat[Unloaded]                    = jsonFormat4(Unloaded.apply)
  implicit val newDeliverySpecifiedFmt: RootJsonFormat[NewDeliverySpecified] = jsonFormat3(NewDeliverySpecified.apply)
}
