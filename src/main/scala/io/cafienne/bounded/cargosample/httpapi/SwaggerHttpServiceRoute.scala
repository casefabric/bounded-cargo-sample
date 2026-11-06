/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.httpapi

import org.apache.pekko.actor.ActorSystem
import com.github.swagger.pekko._
import com.github.swagger.pekko.model.Info

class SwaggerHttpServiceRoute(val system: ActorSystem) extends SwaggerHttpService {
  implicit val actorSystem: ActorSystem = system

  override val apiClasses: Set[Class[?]] = Set(classOf[HttpApiEndpoint], classOf[CargoRoute])

  override val basePath    = "/"        // the basePath for the API you are exposing
  override val apiDocsPath = "api-docs" // where you want the swagger-json endpoint exposed

  override val info = Info(
    description = """Cargo sample services.
      |
      |Allows..
      |
      """.stripMargin,
    version = "1.0.0"
  )

  def swaggerUIRoute =
    get {
      routes ~
        pathPrefix("") {
          pathEndOrSingleSlash {
            getFromResource("swagger-ui/index.html")
          }
        } ~ getFromResourceDirectory("swagger-ui")
    }

}
