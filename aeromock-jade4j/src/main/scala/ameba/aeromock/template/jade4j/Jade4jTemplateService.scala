package ameba.aeromock.template.jade4j

import java.io.{FileNotFoundException, StringWriter, Writer}

import ameba.aeromock.core.annotation.TemplateIdentifier
import ameba.aeromock.core.http.{ParsedRequest, RequestManager}
import ameba.aeromock.data.InstanceProjection
import ameba.aeromock.helper._
import ameba.aeromock.template.{TemplateAssertError, TemplateAssertFailure, TemplateAssertResult, TemplateService}
import ameba.aeromock.{AeromockTemplateNotFoundException, AeromockTemplateParseException}
import de.neuland.jade4j.exceptions.JadeParserException

import scala.language.dynamics
import scalaz.Scalaz._

/**
 * [[ameba.aeromock.template.TemplateService]] for Jade4J.
 * @author stormcat24
 */
@TemplateIdentifier(name = "jade4j", configType = classOf[Jade4jConfigDef])
class Jade4jTemplateService(config: Jade4jConfig) extends TemplateService {

  val configuration = JadeConfigurationFactory.create(project, config)

  /**
   * @inheritdoc
   */
  override def renderHtml(request: ParsedRequest, projection: InstanceProjection): String = {
    val templatePath = if (request.url.startsWith("/")) {
      request.url.substring(1, request.url.length()) + extension
    } else {
      request.url + extension
    }

    val template = try {
      configuration.getTemplate(templatePath)
    } catch {
      case e: FileNotFoundException => throw new AeromockTemplateNotFoundException(templatePath, e)
      case e: JadeParserException => throw new AeromockTemplateParseException("Failed to parse Jade4j template.", e)
    }

    val proxyMap = projection.toInstanceJava().asInstanceOf[java.util.Map[_, _]]

    val out = new StringWriter
    RequestManager.initializeDataMap(proxyMap)
    configuration.renderTemplate(template, proxyMap.asInstanceOf[java.util.Map[String, AnyRef]], out)
    out.toString
  }

  /**
   * @inheritdoc
   */
  override def templateAssertProcess(templatePath: String): Either[TemplateAssertResult, (Any, Writer) => Unit] = {
    val startTimeMills = System.currentTimeMillis()
    try {
      Right((param: Any, writer: Writer) => {
        configuration.renderTemplate(configuration.getTemplate(templatePath), param.asInstanceOf[java.util.Map[String, AnyRef]], writer)
      })
    } catch {
      case e: JadeParserException => Left(TemplateAssertFailure(getDifferenceSecondsFromNow(startTimeMills), e.getMessage))
      case e: Exception => Left(TemplateAssertError(getDifferenceSecondsFromNow(startTimeMills), e.getMessage))
    }
  }

  /**
   * @inheritdoc
   */
  override def extension: String = config.extension | ".jade"

}
