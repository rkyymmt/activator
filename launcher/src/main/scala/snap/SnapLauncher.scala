package snap

/** Expose for SBT launcher support. */
class SnapLauncher extends xsbti.AppMain {
  def run(configuration: xsbti.AppConfiguration) =
    try {
      val args = configuration.arguments
      // TODO - Parse args and launch UI if necessary (via launcher interface) or SBT itself...
      // We should also test environment for interactive shell.
      println("O SNAP!")
      Exit(0)
    } catch {
      case e: Exception =>
        // TODO - What should we do in the event of an error?
        e.printStackTrace
        Exit(1)
    }
  // Wrapper to return exit codes.
  case class Exit(val code: Int) extends xsbti.Exit
}

