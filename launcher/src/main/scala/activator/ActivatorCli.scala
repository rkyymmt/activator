package activator

import xsbti.{ AppMain, AppConfiguration }
import activator.properties.ActivatorProperties.SCRIPT_NAME
import snap.cache.Actions.cloneTemplate
import snap.cache.DefaultTemplateCache
import java.io.File
import sbt.complete.{ Parser, Parsers }
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem

object ActivatorCli {
  def apply(configuration: AppConfiguration): Int = try {
    System.out.println()
    val name = getApplicationName()
    val system = ActorSystem()
    val projectDir = new File(name).getAbsoluteFile
    // Ok, now we load the template cache...

    // TODO - Configurable durations in some config file somewhere.
    val defaultDuration = Duration(6, SECONDS)
    implicit val timeout = akka.util.Timeout(defaultDuration)
    val cache = DefaultTemplateCache(system)
    // Get all possible names.
    // TODO - Drive this whole thing through futures, if we feel SAUCY enough, rather than waiting for results.
    val metadata = Await.result(cache.metadata, defaultDuration)
    val templateNames = metadata.map(_.name).toSeq.distinct
    System.out.println()
    System.out.println(s"The new application will be created in ${projectDir.getAbsolutePath}")
    System.out.println()
    val templateName = getTemplateName(templateNames)
    // Check validity, and check for direct match first
    val template = (metadata.find(_.name == templateName) orElse
      metadata.find(_.name.toLowerCase contains templateName.toLowerCase))
    template match {
      case Some(t) =>
        System.out.println(s"""OK, application "$name" is being created using the "${t.name}" template.""")
        System.out.println()
        import scala.concurrent.ExecutionContext.Implicits.global
        // TODO - Is this duration ok?
        Await.result(cloneTemplate(cache, t.id, projectDir, Some(name)), Duration(5, MINUTES))
        printUsage(name, projectDir)
        0
      case _ =>
        sys.error("Could not find template with name: $templateName")
    }
  } catch {
    case e: Exception =>
      System.err.println(e.getMessage)
      1
  }

  private def printUsage(name: String, dir: File): Unit = {
    // TODO - Cross-platform-ize these strings! Possibly keep script name in SnapProperties.
    System.out.println(s"""|To run "$name" from the command-line, run:
                           |${dir.getAbsolutePath}/${SCRIPT_NAME} run
                           |
                           |To run the test for "$name" from the command-line, run:
                           |${dir.getAbsolutePath}/${SCRIPT_NAME} test
                           |
                           |To run the Activator UI for "$name" from the command-line, run:
                           |${dir.getAbsolutePath}/${SCRIPT_NAME} ui
                           |""".stripMargin)
  }

  private def getApplicationName(): String = {
    System.out.println("Enter an application name")
    val appNameParser: Parser[String] = {
      import Parser._
      import Parsers._
      token(any.* map { _ mkString "" }, "<application name>")
    }

    readLine(appNameParser) filterNot (_.isEmpty) getOrElse sys.error("No application name specified.")
  }

  private def getTemplateName(possible: Seq[String]): String = {
    val templateNameParser: Parser[String] = {
      import Parser._
      import Parsers._
      token(any.* map { _ mkString "" }, "<template name>").examples(possible.toSet, false)
    }

    System.out.println("Enter a template name, or hit tab to see a list of possible templates")
    readLine(templateNameParser) filterNot (_.isEmpty) getOrElse sys.error("No template name specified.")
  }

  /** Uses SBT complete library to read user input with a given auto-completing parser. */
  private def readLine[U](parser: Parser[U], prompt: String = "> ", mask: Option[Char] = None): Option[U] = {
    val reader = new sbt.FullReader(None, parser)
    reader.readLine(prompt, mask) flatMap { line =>
      val parsed = Parser.parse(line, parser)
      parsed match {
        case Right(value) => Some(value)
        case Left(e) => None
      }
    }
  }
}
