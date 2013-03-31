import sbt._
import scala.annotation.tailrec

object JsChecker {
  // returns list of errors
  def fixAndCheckAll(dir: File, log: Logger): Seq[String] = {
    val files = (PathFinder(dir) ** "*.js").get
      .filterNot(_.getPath.contains("vendor"))
      .filterNot(_.getPath.contains("target"))

    // this probably isn't intended
    if (files.isEmpty)
      throw new RuntimeException("Didn't find any JavaScript files to check whitespace")

    for {
      f <- files
      errors <- check(fix(f, log))
    } yield errors
  }

  private def fixWindowsLineBreaks(s: String): String =
    s.replace("\r\n", "\n")

  // this regex is used on the entire file
  private val trailingWhitespaceRegex = """[\t ]+\n""".r

  private def fixTrailingWhitespace(s: String): String =
    trailingWhitespaceRegex.replaceAllIn(s, "\n")

  private def fixNoNewlineAtEnd(s: String): String =
    if (!s.endsWith("\n"))
      s + "\n"
    else
      s

  @tailrec
  private def fixMultipleNewlinesAtEnd(s: String): String =
    if (s.endsWith("\n\n"))
      fixMultipleNewlinesAtEnd(s.substring(0, s.length - 1))
    else
      s

  // we don't fix indentation whitespace because
  // it requires too much judgment as to what
  // was intended.
  private val fixer = (fixWindowsLineBreaks _)
    .andThen(fixNoNewlineAtEnd)
    .andThen(fixTrailingWhitespace)
    .andThen(fixMultipleNewlinesAtEnd)

  def fix(f: File, log: Logger): File = {
    val content = IO.read(f)

    val fixed = fixer(content)

    if (content != fixed) {
      log.info("Fixing whitespace in " + f)
      IO.write(f, fixed)
    }
    f
  }

  // these regexes are used on single lines
  private val oneSpaceBeforeCommentStar = """^\t* \*""".r
  private val hasLeadingSpaces = """^\t* +\t*""".r
  private val hasTrailingWhitespace = """[\t ]+$""".r

  def check(f: File): Seq[String] = {
    val cbf = Seq.newBuilder[String]
    val content = IO.read(f)
    val name = f.getPath

    def checkLeadingSpaces(line: String, num: Int): Option[String] = {
      if (oneSpaceBeforeCommentStar.findFirstIn(line).isDefined)
        None
      else
        hasLeadingSpaces.findFirstIn(line) map { _ => name + ":" + num + ": space should be a tab" }
    }

    def checkTrailingSpaces(line: String, num: Int): Option[String] = {
      hasTrailingWhitespace.findFirstIn(line) map { _ => name + ":" + num + ": trailing whitespace" }
    }

    content.split("\n") zip Stream.from(1) foreach {
      case (line, num) =>
        for (error <- checkLeadingSpaces(line, num))
          cbf += error
        for (error <- checkTrailingSpaces(line, num))
          cbf += error
    }

    if (content.nonEmpty && !content.endsWith("\n"))
      cbf += name + ": No newline at end of file"

    if (content.endsWith("\n\n"))
      cbf += name + ": Multiple newlines at end of file"

    cbf.result
  }
}
