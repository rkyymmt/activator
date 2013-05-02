import sbt._
import scala.annotation.tailrec

// generic for JS, Scala, Java
object Fixer {
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
  val whitespaceFixer = (fixWindowsLineBreaks _)
    .andThen(fixNoNewlineAtEnd)
    .andThen(fixTrailingWhitespace)
    .andThen(fixMultipleNewlinesAtEnd)

  def fix(f: File, fixer: String => String, log: Logger): File = {
    val content = IO.read(f)

    val fixed = fixer(content)

    if (content != fixed) {
      log.info("Fixing whitespace in " + f)
      IO.write(f, fixed)
    }
    f
  }

  def fixWhitespace(f: File, log: Logger): File =
    fix(f, whitespaceFixer, log)
}
