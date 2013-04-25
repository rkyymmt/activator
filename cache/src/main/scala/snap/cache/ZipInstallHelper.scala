package snap
package cache

import activator.properties.ActivatorProperties
import java.io.File
// This class contains methods that are responsible for pulling
// local caches out of our local zip file.
// We place it in its own object to denote the terribleness of the
// hackery used.
object ZipInstallHelper {
  // This logic is responsible for copying the local template cache (if available)
  // from the distribution exploded-zip directory into the user's local
  // cache.
  def copyLocalCacheIfNeeded(cacheDir: File): Unit = {
    // Ensure template cache exists.
    IO.createDirectory(cacheDir)
    // TODO - use SBT IO library when it's on scala 2.10
    for (templateRepo <- Option(ActivatorProperties.ACTIVATOR_TEMPLATE_LOCAL_REPO) map (new java.io.File(_)) filter (_.isDirectory)) {
      // Now loop over all the files in this repo and copy them into the local cache.
      for {
        file <- IO allfiles templateRepo
        relative <- IO.relativize(templateRepo, file)
        if !relative.isEmpty
        to = new java.io.File(cacheDir, relative)
        if !to.exists
      } if (file.isDirectory) IO.createDirectory(to)
      else IO.copyFile(file, to)
    }
  }
}
