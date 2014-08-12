package ameba.aeromock.core.http

import java.net.{InetSocketAddress, URLDecoder}
import java.util.regex.Pattern

import ameba.aeromock.config.ConfigHolder
import ameba.aeromock.core.script.GroovyScriptRunner
import ameba.aeromock.dsl.routing.RoutingDsl
import ameba.aeromock.helper._
import groovy.lang.Binding
import io.netty.handler.codec.http.FullHttpRequest
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scalaz.Scalaz._
import scalaz._

object HttpRequestProcessor {

  val LOG = LoggerFactory.getLogger(this.getClass)
  val qsPattern = Pattern.compile("""(\?.+)$""")

  def execute(original: FullHttpRequest, remoteAddress: InetSocketAddress): HttpRequestContainer = {

    val decoded = URLDecoder.decode(original.getUri(), "UTF-8")
    val matcher = qsPattern.matcher(decoded)
    val queryString = if (matcher.find()) matcher.group(1) else ""
    val uri = decoded.replace(queryString, "")

    if (uri.startsWith("/aeromock/") || uri == "/favicon.ico") {
      HttpRequestContainer(original, None)
    } else {
      val project = ConfigHolder.getProject

      val staticRoot = project.static match {
        // [注意]Pathのままだと、groovyでimplicit classしたインスタンスが渡されてしまう
        case Success(Some(value)) => value.root.toString().some
        case _ => None
      }

      val builtinMap = Map(
        "STATIC_ROOT" -> staticRoot.getOrElse(null),
        "REQUEST_METHOD" -> original.getMethod().name(),
        "REQUEST_URI" -> original.requestUri,
        "QUERY_STRING" -> original.queryString,
        "PARAMETERS" -> original.parsedRequest.queryParameters.asJava,
        "FORM_DATA" -> original.parsedRequest.formData.asJava,
        "NOW" -> DateTime.now().toDate()
      )

      val requestMap = original.toVariableMap ++ remoteAddress.toVariableMap ++ builtinMap
      RequestManager.initializeRequestMap(requestMap)

      if (!project.routingScript.exists) {
        HttpRequestContainer(original, None)
      } else {
        val binding = new Binding
        binding.setVariable("routing", new RoutingDsl)
        requestMap.foreach(pair => binding.setVariable(pair._1, pair._2))
        val runner = new GroovyScriptRunner[String](project.routingScript)
        val routingResult = runner.run(binding)

        if (routingResult != null && uri != routingResult) {
          LOG.info("(Rewrite Finish) %s -> %s".format(uri, routingResult))
          HttpRequestContainer(original, Some(original.copy().setUri(routingResult)))
        } else {
          HttpRequestContainer(original, None)
        }
      }
    }
  }

}
