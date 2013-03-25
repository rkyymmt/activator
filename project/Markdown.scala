import sbt._


object Markdown {

  def parseIntoHtml(f: File): String = {
    import org.pegdown._
    val parser = new PegDownProcessor(Extensions.HARDWRAPS)
    val content = IO read f
    parser markdownToHtml content
  }
  
  def tidyHtml(in: String): String = {
    import org.w3c.tidy.Tidy
    val tidy = new Tidy
    tidy setXHTML false
    val input = new java.io.StringReader(in)
    val output = new java.io.StringWriter
    try tidy.parse(input, output)
    finally {
      input.close()
      output.close()
    }
    output.toString
  }

  // TODO - provide outer context?
  def makeHtml(md: File, html: File, title: String): Unit = 
    IO.write(html,  tidyHtml("""|<!DOCTYPE html>
                       |<html>
                       |<head>
                       |    <title>%s</title>
                       |</head>
                       |<body>
                       |  %s
                       |</body>
                       |</html>""".stripMargin format (title, parseIntoHtml(md))))
}
