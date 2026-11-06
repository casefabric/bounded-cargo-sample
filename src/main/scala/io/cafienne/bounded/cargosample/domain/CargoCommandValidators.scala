/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.domain

import org.apache.pekko.actor.ActorSystem
import io.cafienne.bounded.pekko.ActorSystemProvider
import io.cafienne.bounded.pekko.persistence.ReadJournalProvider
import io.cafienne.bounded.aggregate.ValidateableCommand
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol.{
  CargoDomainCommand,
  CargoNotFound,
  PlanCargo,
  SpecifyNewDelivery
}

import scala.concurrent.{ExecutionContext, Future}

trait CargoCommandValidators {
  implicit val PlanCargoValidator: ValidateableCommand[PlanCargo]
  implicit val SpecifyNewDeliveryValidator: ValidateableCommand[SpecifyNewDelivery]
}

trait ExistenceChecker extends ActorSystemProvider with ReadJournalProvider {
  implicit val ec: ExecutionContext = system.dispatcher

  /**
    * A cargo exists when its journal stream (`cargo|<cargoId>`) holds at least one event.
    */
  def exists[A <: CargoDomainCommand](cmd: A): Future[A] =
    readJournal
      .currentEventsByPersistenceId(Cargo.Type.persistenceId(cmd.aggregateRootId).id, 0, 1)
      .runFold(false)((_, _) => true)
      .flatMap(f =>
        if (f) Future.successful(cmd)
        else
          Future
            .failed(CargoNotFound(s"Cargo with id ${cmd.aggregateRootId} not found while processing command $cmd"))
      )
}

class CargoCommandValidatorsImpl(actorSystem: ActorSystem) extends CargoCommandValidators {

  implicit val PlanCargoValidator: ValidateableCommand[PlanCargo]                   = new PlanCargoValidator()
  implicit val SpecifyNewDeliveryValidator: ValidateableCommand[SpecifyNewDelivery] =
    new CargoCommandValidator[SpecifyNewDelivery](actorSystem)
}

class PlanCargoValidator() extends ValidateableCommand[PlanCargo] {
  override def validate(cmd: PlanCargo): Future[PlanCargo] = {
    Future.successful(cmd)
  }
}

class CargoCommandValidator[T <: CargoDomainCommand](actorSystem: ActorSystem)
    extends ValidateableCommand[T]
    with ExistenceChecker {
  override implicit def system: ActorSystem = actorSystem

  override def validate(cmd: T): Future[T] = exists[T](cmd)
}
