package snap
package cache

import java.io.File

trait RemoteTemplateRepository {
  /**
   * Downloads a remote template into the given local directory.
   *
   * Throws on any error or if template ids do not exist.
   */
  def resolveTemplateTo(templateId: String, localDir: File): File

  /**
   * Checks to see if there's a new index in the remote repository
   * @param currentHash - The old identifier for the index file.
   */
  def hasNewIndex(currentHash: String): Boolean

  /**
   * Resolves the new remote index file to the local index directory.
   * @param indexDirOrFile - The directory or file location where the new index
   *   should be written.
   *
   * @return The new hash of the index.
   */
  def resolveIndexTo(indexDirOrFile: File): String
}
