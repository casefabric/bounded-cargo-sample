# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A sample Scala 3 / Apache Pekko application showing the [bounded framework](https://cafienne.io/bounded) (`io.cafienne.bounded`, version pinned in `build.sbt` as `boundedVersion`) for DDD, CQRS and event sourcing. The domain is the Cargo example from Eric Evans' DDD book. It is a single sbt 2 module with one aggregate (`Cargo`), one projection and a small pekko-http API. The bounded framework source is usually checked out next to this project in `../bounded-framework`; read its README and `bounded-core` sources there when an API question comes up.

## Commands

The build is sbt 2 (`project/build.properties`) and needs Java 17 or later; the default Java on this machine works. sbt 2 takes several commands as one quoted, semicolon-separated argument.

```bash
sbt compile            # also runs scalafmt (scalafmtOnCompile = true) and adds license headers (sbt-header)
sbt test               # all ScalaTest specs, in a forked JVM
sbt "testOnly io.cafienne.bounded.cargosample.domain.CargoAggregateRootActorSpec"   # single spec
sbt "testOnly *CargoDeliverySpec"      # wildcard on the class name; note CargoRouteSpec.scala defines class CargoDeliverySpec
sbt "compile; Test/compile"            # several commands in one invocation
sbt run                # starts Boot (forked); HTTP on 0.0.0.0:8100 (application.conf), Swagger UI served by SwaggerHttpServiceRoute
sbt scalafmtCheck      # formatting check without rewriting; config in project/.scalafmt.conf (scala3 dialect, maxColumn 120)
sbt Docker/publishLocal   # sbt-native-packager Docker image (base eclipse-temurin:21-jre-alpine)
```

Running locally writes state under `target/journal` (LevelDB event store, with the view offsets in `target/journal/lmdb_offsets`), `target/snapshots` and `run/` (LMDB read model). Delete those directories to reset the event store.

## Architecture

Everything lives in `io.cafienne.bounded.cargosample`. The flow is: HTTP API → `RouterCommandGateway` → `Cargo` aggregate root actor → events persisted, tagged with the aggregate type → `CargoViewWriter` materializer → LMDB read model → `CargoQueries` → HTTP API.

- **`Boot`** wires everything by hand (no DI framework): LMDB client, `CargoQueriesImpl`, `CargoViewWriter` mixed with `ReadJournalOffsetStore`, `EventMaterializers`, `RouterCommandGateway(system, idleTime, CargoCreator)`, `HttpApiEndpoint`. The HTTP server only starts after `eventMaterializers.startUp(true)` has finished replaying events.
- **`domain`** (write side)
  - `CargoDomainProtocol` holds all commands (`PlanCargo`, `SpecifyNewDelivery`, `Loading`, `Unloading`), events (`CargoPlanned`, `NewDeliverySpecified`, `Loaded`, `Unloaded`), ids, metadata and domain exceptions (`LoadingFailure`, `CargoNotFound`). Commands carry `CargoCommandMetaData`; events carry `CargoMetaData` created via `CargoMetaData.fromCommand`, which links the event to the originating `commandId`.
  - `Cargo` extends bounded's `AggregateRootActor[CargoAggregateState]`. `handleCommand` returns `Ok(Seq(events))` or `Ko(failure)`; `newState` builds state from the first event; `CargoAggregateState.update` folds subsequent events. `Cargo.Type` (`AggregateType("cargo")`) is the first part of every persistence id (`cargo|<cargoId>`) and the tag bounded puts on every stored event; the read side subscribes to it. Never rename it without migrating the journal.
  - `CargoCreator` is the bounded `AggregateRootCreator` that supplies `Props`; this is where aggregate dependencies (e.g. `LocationsProvider`) are injected.
  - `CargoCommandValidators` provides the implicit `ValidateableCommand[T]` instances the `CommandGateway` requires. `CargoCommandValidator` checks aggregate existence by reading the journal stream `Cargo.Type.persistenceId(id)` (`ExistenceChecker`); `PlanCargoValidator` always passes. `CargoRoute` extends `CargoCommandValidatorsImpl` to bring these implicits into scope.
- **`persistence`**: `CargoDomainEventJsonProtocol` holds spray-json formats for events, used by the HTTP layer to render replies, not by the journal. Tagging is done by bounded's `AggregateRootActor` itself (it wraps events in `Tagged`), so there is no event adapter. Events are serialized with Pekko's `jackson-json` binding on `io.cafienne.bounded.aggregate.DomainEvent`; `java.io.Serializable` is bound to `none` so unregistered event types fail fast.
- **`eventmaterializers`** (read side): `CargoViewWriter` is an abstract class extending bounded's `AbstractReplayableEventMaterializer`; it names `aggregateType = Cargo.Type` and writes `CargoViewItem` JSON into LMDB via `LmdbClient`. The offset store is mixed in where it is instantiated: `ReadJournalOffsetStore` in `Boot` (LMDB next to the LevelDB journal), `OffsetStoreProvider` (in-memory) in tests. `CargoQueriesImpl` reads the same LMDB store.
- **`httpapi`**: `HttpApiEndpoint` composes the routes and wraps them in its exception and rejection handlers (all errors are returned as `ErrorResponse` JSON). `CargoRoute` has the Swagger-annotated (Jakarta REST annotations, swagger-pekko-http) endpoints `GET /cargo/{uuid}` (query side) and `POST /cargo` (sends `PlanCargo` through `commandGateway.sendAndAsk` and maps `Ok`/`Ko` replies to HTTP statuses). `HttpJsonProtocol.PlanCargo` is the API request shape, distinct from the domain command.

JSON formats are split by layer: `CargoDomainJsonProtocol` (value types, ids, `OffsetDateTime` truncated to seconds), `CargoDomainEventJsonProtocol` (events), `QueriesJsonProtocol` (read model), `HttpJsonProtocol` (API request/response). Import the one matching the layer you are in. In Scala 3 every spray-json format needs an explicit type and `jsonFormatN(Type.apply)`.

## Testing conventions

- Aggregate tests use bounded-test's `TestableAggregateRoot.givenEvents[Cargo, CargoAggregateState](creator, id, priorEvents*).when(command)` then assert on `ar.events`, `ar.currentState` (a `Future`) or `ar.failure`. The fixture starts its own actor system on the Pekko persistence testkit journal, configured from the test classpath `application.conf` (which switches the LevelDB auto-start off).
- Projection tests use `TestableProjection.givenEvents(Seq(events), Cargo.Type).startProjection(writer)` and `fixture.addEvent(...)`, then assert through `CargoQueriesImpl` against a real LMDB file under `target/`. The fixture no longer clears the journal, so one spec shares one fixture.
- Route tests use `ScalatestRouteTest` with hand-written stub implementations of `CommandGateway` and `CargoQueries`; no actor system persistence involved.
- `SpecConfig.testConfig` is the in-memory Pekko config (`inmemory-journal` from `pekko-persistence-inmemory`, which bounded-core brings in, plus `bounded.eventmaterializers.offsetstore.type = "inmemory"`). The projection spec passes it to its `ActorSystem` explicitly.

## Notes

- `scalafmtOnCompile` and the `AutomateHeaderPlugin` modify source files during `sbt compile`. The header plugin rewrites the copyright line to `2018-<current year>` in every file, so a plain compile dirties the whole tree. Revert those header-only changes before committing unless a header bump is intended.
- lmdbjava needs `--add-opens` on Java 17+; `build.sbt` forks `run` and `test` with those options and adds them to the Docker start script. Running `Boot` any other way needs the same JVM options.
- The LevelDB journal is deprecated in Pekko but still shipped; `application.conf` sets `native = off` because leveldbjni has no Apple Silicon build.
- bounded-core pulls in pekko-persistence-jdbc (built against Slick 3.5) next to Slick 3.6; `build.sbt` declares `libraryDependencySchemes` for Slick so sbt does not fail the build on that eviction.
- The README documents deliberate deviations from the book's model (action-driven protocol instead of data-driven, delivery history derived from events instead of a separate object, keeping the book's `Customer` naming).
