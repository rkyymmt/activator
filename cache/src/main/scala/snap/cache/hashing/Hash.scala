package snap
package cache
package hashing

import java.security.MessageDigest
import java.io.File

/** Typeclass that helps us decide how to hash things. */
trait Hash[T] {
  def hash(t: T): String
}
object Hash {
  protected abstract class DefaultImplicits(alg: String) {
    implicit val file = new FileDigestHasher(alg)
    implicit val userMeta = new UserDefinedTemplateMetadataHasher(alg)
  }
  /** Default typeclasses for  hashing with SHA-1 */
  object sha1 extends DefaultImplicits("SHA-1")
  /** Default typeclasses for  hashing with SHA-512 */
  object sha512 extends DefaultImplicits("SHA-512")
  /** Default typeclasses for  hashing with MD5 */
  object md5 extends DefaultImplicits("MD5")

  /** The default hash used in our system, currently. */
  val default = sha1
}

/** Helper class to implement hashes via message digest. */
abstract class MessageDigestHasher[T](alg: String) extends Hash[T] {
  def hash(t: T): String = {
    val md = MessageDigest.getInstance(alg)
    updateDigest(t, md)
    hashing.digestToHexString(md.digest)
  }
  protected def updateDigest(t: T, md: MessageDigest): Unit
}

/** Helper to create file hashing digests. */
class FileDigestHasher(alg: String) extends MessageDigestHasher[File](alg) {
  protected def updateDigest(file: File, md: MessageDigest): Unit = {
    val in = new java.io.FileInputStream(file);
    val buffer = new Array[Byte](8192)
    try {
      def read(): Unit = in.read(buffer) match {
        case x if x <= 0 => ()
        case size => md.update(buffer, 0, size); read()
      }
      read()
    } finally in.close()
  }
}
/** Helper to create hashes for user defined templates. */
class UserDefinedTemplateMetadataHasher(alg: String) extends MessageDigestHasher[AuthorDefinedTemplateMetadata](alg) {
  protected def updateDigest(user: AuthorDefinedTemplateMetadata, md: MessageDigest): Unit = {
    md.update(user.name.getBytes)
    md.update(user.title.getBytes)
    md.update(user.description.getBytes)
    md.update(user.tags.mkString(",").getBytes)
  }
}
