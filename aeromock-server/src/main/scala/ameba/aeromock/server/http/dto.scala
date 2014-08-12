package ameba.aeromock.server.http

import io.netty.handler.codec.http.HttpResponseStatus

case class RenderResult(content: String, response: Option[CustomResponse], debug: Boolean)

case class CustomResponse(code: Int, headers: Map[String, String]) {
  def getResponseStatus: HttpResponseStatus = HttpResponseStatus.valueOf(code)
}