package ameba.aeromock.template.handlebars

import java.io.{FileNotFoundException, StringWriter, Writer}

import ameba.aeromock.core.annotation.TemplateIdentifier
import ameba.aeromock.core.http.{ParsedRequest, RequestManager}
import ameba.aeromock.data.InstanceProjection
import ameba.aeromock.helper._
import ameba.aeromock.template._
import ameba.aeromock.{AeromockTemplateNotFoundException, AeromockTemplateParseException}
import com.github.jknack.handlebars.HandlebarsException

import scala.language.dynamics
import scalaz.Scalaz._

/**
 * [[ameba.aeromock.template.TemplateService]] for Handlebars.java
 * @author stormcat24
 */
@TemplateIdentifier(name = "handlebars", configType = classOf[HandlebarsConfigDef])
class HandlebarsTemplateService(config: HandlebarsConfig) extends TemplateService {

  val handlebars = HandlebarsFactory.create(project, config)

  /**
   * @inheritdoc
   */
  override def renderHtml(request: ParsedRequest, projection: InstanceProjection): String = {
    val template = try {
      handlebars.compile(request.url)
    } catch {
      case e: FileNotFoundException => throw new AeromockTemplateNotFoundException(request.url + config.suffix, e)
      case e: HandlebarsException => throw new AeromockTemplateParseException(request.url + config.suffix, e)
    }

    val proxyMap = projection.toInstanceJava().asInstanceOf[java.util.Map[_, _]]
    RequestManager.initializeDataMap(proxyMap)

    val out = new StringWriter
    template.apply(proxyMap, out)
    out.toString()
  }

  /**
   * @inheritdoc
   */
  override def templateAssertProcess(templatePath: String): Either[TemplateAssertResult, (Any, Writer) => Unit] = {
    // Handlebars#compileはテンプレートを探すのにsuffixを考慮してしまうため
    val formattedPath = getFormattedPath(templatePath)
    val startTimeMills = System.currentTimeMillis()

    try {
      Right((param: Any, writer: Writer) => handlebars.compile(formattedPath).apply(param, writer))
    } catch {
      case e: HandlebarsException => Left(TemplateAssertFailure(getDifferenceSecondsFromNow(startTimeMills), e.getMessage))
      case e: Exception => Left(TemplateAssertError(getDifferenceSecondsFromNow(startTimeMills), e.getMessage))
    }
  }

  private def getFormattedPath(templatePath: String): String = {
    if (templatePath.endsWith(extension)) templatePath.replaceFirst(extension + "$", "") else templatePath
  }

  def extension: String = config.suffix | ".hbs"
}
