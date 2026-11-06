/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.eventmaterializers

import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol.CargoId

trait CargoQueries {
  import QueriesJsonProtocol._

  def getCargo(cargoId: CargoId): Option[CargoViewItem]

}
