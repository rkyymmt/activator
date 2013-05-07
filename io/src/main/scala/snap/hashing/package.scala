package snap

package object hashing {

  private val HEX_CHARS = "0123456789abcdef".toCharArray

  // Note: This marks the third time I've copied this method...
  // We may want to just make a "Hash helper" library (or rip the one from
  // dbuild).   If we ever want a good BigData story, we need to focus on
  // good hash algorithms and fast ways to hash data/case-classes.
  //  <end of rant>
  def digestToHexString(bytes: Array[Byte]): String = {
    val buf = new StringBuffer
    // TODO - error handling necessary?
    def byteToHex(b: Int) = HEX_CHARS(b)
    for (i <- 0 until bytes.length) {
      val b = bytes(i)
      buf append byteToHex((b >>> 4) & 0x0F)
      buf append byteToHex(b & 0x0F)
    }
    buf.toString
  }

  def hash[T](t: T)(implicit hash: Hash[T]): String = hash(t)

  object files {
    /** Default typeclasses for  hashing with SHA-1 */
    object sha1 extends FileDigestHasher("SHA-1")
    /** Default typeclasses for  hashing with SHA-512 */
    object sha512 extends FileDigestHasher("SHA-512")
    /** Default typeclasses for  hashing with MD5 */
    object md5 extends FileDigestHasher("MD5")
  }
}
