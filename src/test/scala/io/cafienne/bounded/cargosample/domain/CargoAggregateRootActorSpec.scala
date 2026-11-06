/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.domain

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.Timeout
import io.cafienne.bounded.cargosample.domain.Cargo.CargoAggregateState
import io.cafienne.bounded.cargosample.domain.Cargo.CarrierMovement
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol._
import io.cafienne.bounded.cargosample.SpecConfig
import io.cafienne.bounded.test.TestableAggregateRoot
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration._

class CargoAggregateRootActorSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  implicit val timeout: Timeout    = Timeout(60.seconds)
  implicit val system: ActorSystem = ActorSystem("CargoTestSystem", SpecConfig.testConfig)

  // Creation of Aggregate Roots that make use of dependencies is organized via the Creator
  // as a separate class that contains the required dependencies.
  val cargoAggregateRootCreator = new CargoCreator(system, new FixedLocationsProvider())

  val userId1     = CargoUserId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee572"))
  val userContext = Some(new CargoUserContext(userId1, List.empty))
  val metaData    = CargoCommandMetaData(OffsetDateTime.now(ZoneOffset.UTC), userContext)

  "CargoAggregateRoot" must {

    "Create a new aggregate" in {
      val cargoId2           = CargoId(java.util.UUID.fromString("49A6553D-7E0A-49E8-BE20-925839F524B2"))
      val trackingId         = TrackingId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee573"))
      val routeSpecification = DeliverySpecification(
        Location("home"),
        Location("destination"),
        OffsetDateTime.parse("2018-03-03T10:15:30+01:00")
      )

      val ar = TestableAggregateRoot
        .givenEvents[Cargo, CargoAggregateState](cargoAggregateRootCreator, cargoId2.idAsString)
        .when(PlanCargo(metaData, cargoId2, trackingId, routeSpecification))

      ar.events should contain(
        CargoPlanned(CargoMetaData.fromCommand(metaData), cargoId2, trackingId, routeSpecification)
      )
      val targetState = CargoAggregateState(trackingId, routeSpecification)
      ar.currentState map { state =>
        assert(state.get == targetState)
      }
    }

    "Change the delivery specification for an existing Cargo Delivery Using AggregateRootTestFixture" in {
      val cargoId3           = CargoId(java.util.UUID.fromString("D31E3C57-E63E-4AD5-A00B-E5FA9196E80D"))
      val trackingId         = TrackingId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee573"))
      val routeSpecification = DeliverySpecification(
        Location("home"),
        Location("destination"),
        OffsetDateTime.parse("2018-03-03T10:15:30+01:00")
      )
      val cargoPlannedEvent =
        CargoPlanned(CargoMetaData.fromCommand(metaData), cargoId3, trackingId, routeSpecification)

      val newDeliverySpecification = DeliverySpecification(
        Location("home"),
        Location("newDestination"),
        OffsetDateTime.parse("2018-03-04T10:45:45+01:00")
      )
      val specifyNewDeliveryCommand = SpecifyNewDelivery(metaData, cargoId3, newDeliverySpecification)

      val ar = TestableAggregateRoot
        .givenEvents[Cargo, CargoAggregateState](cargoAggregateRootCreator, cargoId3.idAsString, cargoPlannedEvent)
        .when(specifyNewDeliveryCommand)

      // You see that this only shows the events that are 'published' via when
      ar.events should contain(
        NewDeliverySpecified(CargoMetaData.fromCommand(metaData), cargoId3, newDeliverySpecification)
      )

      val targetState = CargoAggregateState(trackingId, newDeliverySpecification)
      ar.currentState map { state =>
        assert(state.get == targetState)
      }
    }

    "Load a cargo for an Iterary" in {
      val cargoId3              = CargoId(java.util.UUID.fromString("D31E3C57-E63E-4AD5-A00B-E5FA9196E80D"))
      val trackingId            = TrackingId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee573"))
      val deliverySpecification = DeliverySpecification(
        Location("home"),
        Location("destination"),
        OffsetDateTime.parse("2018-03-03T10:15:30Z")
      )
      val cargoPlannedEvent =
        CargoPlanned(CargoMetaData.fromCommand(metaData), cargoId3, trackingId, deliverySpecification)
      val vesselVoyageId = VesselVoyageId(UUID.fromString("AC1000CD-20FE-48B2-8828-F51F1C3114C4"))
      val loadCargo      = Loading(
        metaData,
        cargoId3,
        Location("amsterdam"),
        vesselVoyageId
      )

      val ar = TestableAggregateRoot
        .givenEvents[Cargo, CargoAggregateState](cargoAggregateRootCreator, cargoId3.idAsString, cargoPlannedEvent)
        .when(loadCargo)

      // You see that this only shows the events that are 'published' via when
      ar.events should contain(
        Loaded(CargoMetaData.fromCommand(metaData), cargoId3, Location("amsterdam"), vesselVoyageId)
      )
      val targetState = CargoAggregateState(trackingId, deliverySpecification, Some(CarrierMovement(vesselVoyageId)))
      ar.currentState map { state =>
        assert(state.get == targetState)
      }
    }

    "Cannot load a cargo for an Iterary" in {
      val cargoId3              = CargoId(java.util.UUID.fromString("D31E3C57-E63E-4AD5-A00B-E5FA9196E80D"))
      val trackingId            = TrackingId(UUID.fromString("53f53841-0bf3-467f-98e2-578d360ee573"))
      val deliverySpecification = DeliverySpecification(
        Location("home"),
        Location("destination"),
        OffsetDateTime.parse("2018-03-03T10:15:30+01:00")
      )
      val cargoPlannedEvent =
        CargoPlanned(CargoMetaData.fromCommand(metaData), cargoId3, trackingId, deliverySpecification)
      val vesselVoyageId   = VesselVoyageId(UUID.fromString("AC1000CD-20FE-48B2-8828-F51F1C3114C4"))
      val cargoLoadedEvent = Loaded(
        CargoMetaData.fromCommand(metaData),
        cargoId3,
        Location("amsterdam"),
        vesselVoyageId
      )

      val newLoad = Loading(
        metaData,
        cargoId3,
        Location("antwerp"),
        VesselVoyageId(UUID.fromString("629167F9-2095-485F-B3E2-D38FDEB7A345"))
      )

      val ar = TestableAggregateRoot
        .givenEvents[Cargo, CargoAggregateState](
          cargoAggregateRootCreator,
          cargoId3.idAsString,
          cargoPlannedEvent,
          cargoLoadedEvent
        )
        .when(newLoad)

      ar.failure match {
        case LoadingFailure(msg, ex) => msg.contains("already") should be(true)
        case other                   => fail("Expecting a LoadingFailure")
      }
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system, 30.seconds, verifySystemShutdown = true)
  }

}
