/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.eventmaterializers

import org.apache.pekko.actor.ActorSystem
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol

class CargoQueriesImpl(lmdbClient: LmdbClient)(implicit val system: ActorSystem) extends CargoQueries {

  import spray.json._
  import QueriesJsonProtocol.cargoViewItemFmt

  override def getCargo(cargoId: CargoDomainProtocol.CargoId): Option[QueriesJsonProtocol.CargoViewItem] = {
    lmdbClient.get(cargoId.idAsString).map { value =>
      JsonParser(value).convertTo[QueriesJsonProtocol.CargoViewItem]
    }
  }

}
