/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.domain

import org.apache.pekko.actor._
import io.cafienne.bounded.aggregate._
import io.cafienne.bounded.cargosample.domain.Cargo.CargoAggregateState
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol._

import scala.collection.immutable.Seq

/**
  * Aggregate root that keeps the logic of the cargo.
  * @param cargoId unique identifier for cargo.
  */
class Cargo(
  cargoId: String,
  locationsProvider: LocationsProvider
) extends AggregateRootActor[CargoAggregateState] {

  override def aggregateId: String = cargoId

  override def aggregateType: AggregateType = Cargo.Type

  override def handleCommand(command: DomainCommand, state: Option[CargoAggregateState]): Reply = {
    command match {
      case cmd: PlanCargo =>
        Ok(
          Seq(
            CargoPlanned(
              CargoMetaData.fromCommand(cmd.metaData),
              cmd.cargoId,
              cmd.trackingId,
              cmd.deliverySpecification
            )
          )
        )
      case cmd: SpecifyNewDelivery =>
        Ok(Seq(NewDeliverySpecified(CargoMetaData.fromCommand(cmd.metaData), cmd.cargoId, cmd.deliverySpecification)))
      case cmd: Loading =>
        if (state.isDefined && state.get.currentCarrierMovement.isDefined) {
          Ko(LoadingFailure(s"Cannot load a new Cargo as there is already a shipment loaded $state"))
        } else {
          Ok(Seq(Loaded(CargoMetaData.fromCommand(cmd.metaData), cmd.cargoId, cmd.location, cmd.vesselVoyageId)))
        }
      case cmd: Unloading =>
        Ok(Seq(Unloaded(CargoMetaData.fromCommand(cmd.metaData), cmd.cargoId, cmd.location, cmd.vesselVoyageId)))
      case other => Ko(new UnexpectedCommand(other))
    }
  }

  override def newState(evt: DomainEvent): Option[CargoAggregateState] = {
    evt match {
      case evt: CargoPlanned =>
        Some(CargoAggregateState(evt.trackingId, evt.deliverySpecification))
      case _ =>
        throw new IllegalArgumentException(s"Event $evt is not valid to create a new CargoAggregateState")
    }
  }

}

object Cargo {

  /**
    * The aggregate type: the first part of every persistence id (`cargo|<cargoId>`) and the tag of every
    * stored event, which is how the event materializers find them. Never rename it without migrating the journal.
    */
  val Type: AggregateType = AggregateType("cargo")

  case class CarrierMovement(versselVoyageId: VesselVoyageId)

  case class CargoAggregateState(
    trackingId: TrackingId,
    deliverySpecification: DeliverySpecification,
    currentCarrierMovement: Option[CarrierMovement] = None
  ) extends AggregateState[CargoAggregateState] {

    override def update(evt: DomainEvent): Option[CargoAggregateState] = {
      evt match {
        case CargoPlanned(_, _, newTrackingId, newDeliverySpecification) =>
          Some(CargoAggregateState(newTrackingId, newDeliverySpecification))
        case NewDeliverySpecified(_, _, newDeliverySpecification) =>
          Some(this.copy(deliverySpecification = newDeliverySpecification))
        case Loaded(_, _, _, vesselVoyageId) =>
          Some(this.copy(currentCarrierMovement = Some(CarrierMovement(vesselVoyageId))))
        case Unloaded(_, _, _, _) =>
          Some(this.copy(currentCarrierMovement = None))
        case _ => Some(this)
      }
    }
  }

}

/**
  * The Aggregate Root needs dependencies. These are given via the Creator.
  * A Creator returns the props that is used to create an Aggregate Root Actor.
  * @param system as a sample dependency the actor system is passed.
  * @param locations a dependency is used inside the Aggregate Root.
  */
class CargoCreator(system: ActorSystem, locations: LocationsProvider) extends AggregateRootCreator {

  override def props(idToCreate: String): Props = Props(new Cargo(idToCreate, locations))
}
