/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.domain

import java.util.UUID
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol._
import spray.json.{RootJsonFormat, _}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CargoDomainJsonProtocol extends DefaultJsonProtocol {

  def jsonEnum[T <: Enumeration](enu: T): JsonFormat[enu.Value] =
    new JsonFormat[enu.Value] {
      def write(obj: enu.Value): JsValue = JsString(obj.toString)

      def read(json: JsValue): enu.Value =
        json match {
          case JsString(txt) => enu.withName(txt)
          case something     =>
            throw DeserializationException(s"Expected a value from enum $enu instead of $something")
        }
    }

  implicit object OffsetDateTimeJsonFormat extends RootJsonFormat[OffsetDateTime] {

    def write(dt: OffsetDateTime): JsValue =
      JsString(
        dt.truncatedTo(ChronoUnit.SECONDS)
          .format(DateTimeFormatter.ISO_DATE_TIME)
      )

    def read(value: JsValue): OffsetDateTime =
      value match {
        case JsString(v) =>
          OffsetDateTime.parse(v, DateTimeFormatter.ISO_DATE_TIME)
        case _ =>
          deserializationError(s"value $value not conform ISO8601 (yyyy-MM-dd'T'HH:mm:ssZZ) where time is optional")
      }
  }

  implicit object JavaUUIDFormat extends RootJsonFormat[UUID] {
    override def write(obj: UUID): JsValue = JsString(obj.toString)

    override def read(json: JsValue): UUID =
      json match {
        case JsString(v) => UUID.fromString(v)
        case _           =>
          deserializationError(s"value $json cannot be deserialized to a UUID")
      }
  }

  implicit object cargoUserIdFmt extends RootJsonFormat[CargoUserId] {
    override def write(obj: CargoUserId): JsValue = JsString(obj.id.toString)

    override def read(json: JsValue): CargoUserId =
      json match {
        case JsString(v) => CargoUserId(UUID.fromString(v))
        case _           =>
          deserializationError(s"value $json cannot be deserialized to a CargoUserId")
      }
  }

  implicit object vesselVoyageIdFmt extends RootJsonFormat[VesselVoyageId] {
    override def write(obj: VesselVoyageId): JsValue = JsString(obj.id.toString)

    override def read(json: JsValue): VesselVoyageId =
      json match {
        case JsString(v) => VesselVoyageId(UUID.fromString(v))
        case _           =>
          deserializationError(s"value $json cannot be deserialized to a VesselVoyageId")
      }
  }

  implicit object cargoIdFmt extends RootJsonFormat[CargoId] {
    override def write(obj: CargoId): JsValue = JsString(obj.id.toString)

    override def read(json: JsValue): CargoId =
      json match {
        case JsString(v) => CargoId(UUID.fromString(v))
        case _           =>
          deserializationError(s"value $json cannot be deserialized to a CargoId")
      }
  }

  implicit val CargoUserContextFmt: RootJsonFormat[CargoUserContext] = jsonFormat2(CargoUserContext.apply)
  implicit val CargoCommandMetaDataJsonFormat: RootJsonFormat[CargoCommandMetaData] = jsonFormat3(
    CargoCommandMetaData.apply
  )
  implicit val MetaDataJsonFormat: RootJsonFormat[CargoMetaData] = jsonFormat3(CargoMetaData.apply)

  implicit object chargeSessionIdFmt extends RootJsonFormat[TrackingId] {
    override def write(obj: TrackingId): JsValue = JsString(obj.id.toString)

    override def read(json: JsValue): TrackingId =
      json match {
        case JsString(v) => TrackingId(UUID.fromString(v))
        case _           =>
          deserializationError(s"value $json cannot be deserialized to a TrackingId")
      }
  }

  implicit val locationFmt: RootJsonFormat[Location]                           = jsonFormat1(Location.apply)
  implicit val deliverySpecificationFmt: RootJsonFormat[DeliverySpecification] = jsonFormat3(
    DeliverySpecification.apply
  )

  implicit object CargoNotFoundFmt extends RootJsonFormat[CargoNotFound] {
    override def read(json: JsValue): CargoNotFound =
      json.asJsObject.getFields("message", "cause") match {
        case Seq(JsString(message), JsString(cause)) =>
          CargoNotFound(message, new Throwable(cause))
        case Seq(JsString(message), JsNull) => CargoNotFound(message)
      }

    override def write(obj: CargoNotFound): JsValue =
      JsObject(
        Map("message" -> JsString(obj.msg)).++:(
          Option(obj.getCause)
            .fold(Map.empty[String, JsValue])(cause => Map("cause" -> JsString(cause.getMessage)))
        )
      )
  }

  implicit val planCargoFmt: RootJsonFormat[PlanCargo]                   = jsonFormat4(PlanCargo.apply)
  implicit val specifyNewDeliveryFmt: RootJsonFormat[SpecifyNewDelivery] = jsonFormat3(SpecifyNewDelivery.apply)

}
