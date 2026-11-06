/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.eventmaterializers

import java.time.OffsetDateTime
import spray.json._

object QueriesJsonProtocol extends DefaultJsonProtocol {
  import io.cafienne.bounded.cargosample.domain.CargoDomainJsonProtocol._
  case class CargoViewItem(
    id: String,
    origin: String,
    destination: String,
    deliveryDueDate: OffsetDateTime
  )

  implicit val cargoViewItemFmt: RootJsonFormat[CargoViewItem] = jsonFormat4(CargoViewItem.apply)

}
