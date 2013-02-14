package snap

import java.io.File

object Platform {
  val isWindows = sys.props("os.name").toLowerCase.indexOf("win") >= 0

  def filename(f: File): String = {
    val raw = f.getCanonicalPath
    if (isWindows) mungeWindows(raw)
    else raw
  }
  def file(n: String): File = {
    val name = if (isWindows) unmungeWindows(n) else n
    new File(name)
  }

  // TODO - URI encoding...
  private def mungeWindows(name: String): String =
    name.replaceAll("\\\\", "/")
  private def unmungeWindows(name: String): String =
    name.replaceAll("/", "\\\\")
}
