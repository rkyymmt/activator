package com.typesafe.sbtchild
package probe

import java.io.File

/** Helper methods to make Java hashing libraries not quite so annoying... */
object Hashing {
  import java.security.MessageDigest

  def sha512(f: File): String =
    digest(MessageDigest.getInstance("SHA-512"))(f)

  def sha1(f: File): String =
    digest(MessageDigest.getInstance("SHA-1"))(f)

  def digest(md: MessageDigest)(file: File): String = {
    val in = new java.io.FileInputStream(file);
    val buffer = new Array[Byte](8192)
    try {
      // Tail recursive read helper
      def read(): Unit = in.read(buffer) match {
        case x if x <= 0 => ()
        case size =>
          md.update(buffer, 0, size);
          read()
      }
      read()
    } finally in.close()
    val sha = convertToHex(md.digest)
    sha
  }

  def convertToHex(data: Array[Byte]): String = {
    val buf = new StringBuffer
    def byteToHex(b: Int) =
      if ((0 <= b) && (b <= 9)) ('0' + b).toChar
      else ('a' + (b - 10)).toChar
    for (i <- 0 until data.length) {
      buf append byteToHex((data(i) >>> 4) & 0x0F)
      buf append byteToHex(data(i) & 0x0F)
    }
    buf.toString
  }
}