/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.domain

import io.cafienne.bounded.aggregate._

import java.time.OffsetDateTime
import java.util.UUID

import scala.util.control.NoStackTrace

object CargoDomainProtocol {

  trait Id {
    def idAsString: String
  }

  trait UserId extends Id

  trait AggregateRootId extends Id

  trait UserContext {
    def userId: UserId

    def roles: List[String]
  }

  trait CommandMetaData {
    def timestamp: OffsetDateTime

    def userContext: Option[CargoUserContext]

    val commandId: UUID = UUID.randomUUID()
  }

  trait MetaData {
    def timestamp: OffsetDateTime

    def userContext: Option[CargoUserContext]

    def causedByCommand: Option[UUID]
  }

  case class CargoUserId(id: UUID) extends UserId {
    override def idAsString: String = id.toString
  }

  case class CargoId(id: UUID) extends AggregateRootId {
    override def idAsString: String = id.toString

    override def toString: String = id.toString
  }

  case class CargoUserContext(userId: CargoUserId, roles: List[String]) extends UserContext

  case class CargoCommandMetaData(
    timestamp: OffsetDateTime,
    userContext: Option[CargoUserContext],
    override val commandId: UUID = UUID.randomUUID()
  ) extends CommandMetaData

  case class CustomerId(id: UUID) extends Id {
    override def idAsString: String = id.toString
  }

  case class VesselVoyageId(id: UUID) extends Id {
    override def idAsString: String = id.toString
  }

  case class TrackingId(id: UUID)
  case class Location(name: String)
  case class DeliverySpecification(origin: Location, destination: Location, arrivalDeadline: OffsetDateTime)

  /**
    * All commands for the Cargo are extended via DomainCommand.
    * This command expects id,  user context and a timestamp as standard input next to to command specific payload.
    *
    * @see DomainCommand for details.
    */
  trait CargoDomainCommand extends DomainCommand {
    val metaData: CargoCommandMetaData
  }

  case class CargoMetaData(
    timestamp: OffsetDateTime,
    userContext: Option[CargoUserContext],
    causedByCommand: Option[UUID]
  ) extends MetaData

  object CargoMetaData {
    def fromCommand(
      metadata: CargoCommandMetaData
    ): CargoMetaData = {
      CargoMetaData(
        metadata.timestamp,
        metadata.userContext,
        Some(metadata.commandId)
      )
    }
  }

  /**
    * All events for the Cargo are extended via DomainEvent
    * This event expects id, tenant(id), user context and a timestamp as standard input next to the event specific payload.
    */
  trait CargoDomainEvent extends DomainEvent

  trait HandlingEvent extends CargoDomainEvent

  // Commands
  case class PlanCargo(
    metaData: CargoCommandMetaData,
    cargoId: CargoId,
    trackingId: TrackingId,
    deliverySpecification: DeliverySpecification
  ) extends CargoDomainCommand {
    override def aggregateRootId: String = cargoId.idAsString
  }

  case class SpecifyNewDelivery(
    metaData: CargoCommandMetaData,
    cargoId: CargoId,
    deliverySpecification: DeliverySpecification
  ) extends CargoDomainCommand {
    override def aggregateRootId: String = cargoId.idAsString
  }

  case class SpecifyNewRoute(
    metaData: CargoCommandMetaData,
    cargoId: CargoId,
    deliverySpecification: DeliverySpecification
  )
  case class Loading(
    metaData: CargoCommandMetaData,
    cargoId: CargoId,
    location: Location,
    vesselVoyageId: VesselVoyageId
  ) extends CargoDomainCommand {
    override def aggregateRootId: String = cargoId.idAsString
  }

  case class Unloading(
    metaData: CargoCommandMetaData,
    cargoId: CargoId,
    location: Location,
    vesselVoyageId: VesselVoyageId
  ) extends CargoDomainCommand {
    override def aggregateRootId: String = cargoId.idAsString
  }

  // Events
  case class CargoPlanned(
    metaData: CargoMetaData,
    cargoId: CargoId,
    trackingId: TrackingId,
    deliverySpecification: DeliverySpecification
  ) extends CargoDomainEvent {
    override def id: String = cargoId.idAsString
  }

  // case class NewRouteSpecified(metaData: CargoMetaData, CargoId: CargoId, routeSpecification: RouteSpecification)
  case class NewDeliverySpecified(
    metaData: CargoMetaData,
    cargoId: CargoId,
    deliverySpecification: DeliverySpecification
  ) extends CargoDomainEvent {
    override def id: String = cargoId.idAsString
  }

  case class Loaded(metaData: CargoMetaData, cargoId: CargoId, location: Location, vesselVoyageId: VesselVoyageId)
      extends HandlingEvent {
    override def id: String = cargoId.idAsString
  }

  case class Unloaded(metaData: CargoMetaData, cargoId: CargoId, location: Location, vesselVoyageId: VesselVoyageId)
      extends HandlingEvent {
    override def id: String = cargoId.idAsString
  }

  trait CargoDomainException extends NoStackTrace with HandlingFailure {
    val msg: String
  }

  class LoadingFailure(override val msg: String) extends Exception(msg) with CargoDomainException {
    def this(msg: String, cause: Throwable) = {
      this(msg)
      initCause(cause)
    }
  }

  object LoadingFailure {
    def apply(msg: String): LoadingFailure =
      new LoadingFailure(msg)
    def apply(msg: String, cause: Throwable): LoadingFailure =
      new LoadingFailure(msg, cause)

    def unapply(e: LoadingFailure): Option[(String, Option[Throwable])] =
      Some((e.getMessage, Option(e.getCause)))
  }

  class CargoNotFound(override val msg: String) extends Exception(msg) with CargoDomainException {
    def this(msg: String, cause: Throwable) = {
      this(msg)
      initCause(cause)
    }
  }

  object CargoNotFound {
    def apply(msg: String): CargoNotFound =
      new CargoNotFound(msg)
    def apply(msg: String, cause: Throwable): CargoNotFound =
      new CargoNotFound(msg, cause)

    def unapply(e: CargoNotFound): Option[(String, Option[Throwable])] =
      Some((e.getMessage, Option(e.getCause)))
  }

}
