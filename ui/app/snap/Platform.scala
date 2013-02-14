package snap

import java.io.File

/** We extract this so we can test it on non-windows machines. */
private[snap] class Platform(val isWindows: Boolean) {
  def getClientFriendlyFilename(f: File): String = {
    // TODO - We can't use canonical path
    // because we don't want to follow symlinks...
    // If you change this, you will break the UI, so DON'T.
    val raw = f.getAbsolutePath
    if (isWindows) mungeWindows(raw)
    else raw
  }
  def fromClientFriendlyFilename(n: String): File = {
    val name = if (isWindows) unmungeWindows(n) else n
    new File(name)
  }

  // TODO - Figure out what to do when windows wants a / in the path....
  private def mungeWindows(name: String): String =
    name.replaceAll("\\\\", "/")
  private def unmungeWindows(name: String): String =
    name.replaceAll("/", "\\\\")
}

object Platform extends Platform(sys.props("os.name").toLowerCase.indexOf("win") >= 0)
