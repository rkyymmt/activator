import sbt._

object JsChecker {
  // returns list of errors
  def checkAll(dir: File): Seq[String] = {
    val files = (PathFinder(dir) ** "*.js").get
      .filterNot(_.getPath.contains("vendor"))
      .filterNot(_.getPath.contains("target"))

    // this probably isn't intended
    if (files.isEmpty)
      throw new RuntimeException("Didn't find any JavaScript files to check whitespace")

    for {
      f <- files
      errors <- check(f)
    } yield errors
  }

  def check(f: File): Seq[String] = {
    val cbf = Seq.newBuilder[String]
    val content = IO.read(f)
    val name = f.getPath

    val oneSpaceBeforeCommentStar = """^\t* \*""".r
    val hasLeadingSpaces = """^\t* +\t*""".r
    val hasTrailingWhitespace = """[\t ]+$""".r

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
