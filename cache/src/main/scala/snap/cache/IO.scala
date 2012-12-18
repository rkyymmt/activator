package snap.cache


import java.io._
import java.nio.channels._
// HACK NOTICE - This is mimicked from SBT until we can replace it.

object IO {
  def createDirectory(dir: File) = {
    def failBase = "Could not create directory " + dir
    // Need a retry because mkdirs() has a race condition
    var tryCount = 0
    while (!dir.exists && !dir.mkdirs() && tryCount < 100) { tryCount += 1 }
    if(dir.isDirectory) ()
    else if(dir.exists) sys.error(failBase + ": file exists and is not a directory.")
    else                sys.error(failBase)
  }
  def copyFile(from: File, to: File): Unit = {
    require(from.exists, s"Source file '${from.getAbsolutePath}' does not exist.")
    require(!from.isDirectory, s"Source file '${from.getAbsolutePath }' is a directory.")
    fileInputChannel(from) { in =>
      fileOutputChannel(to) { out =>
        // maximum bytes per transfer according to  from http://dzone.com/snippets/java-filecopy-using-nio
        val max = (64 * 1024 * 1024) - (32 * 1024)
        val total = in.size
        def loop(offset: Long): Long =
          if(offset < total) loop( offset + out.transferFrom(in, offset, max) )
          else               offset
        val copied = loop(0)
        if(copied != in.size)
          sys.error(s"Could not copy '$from' to '$to' ($copied/${in.size} bytes copied)")
			}
		}
  }
  def fileInputChannel[A](file: File)(f: FileChannel => A): A = {
    val input = new FileInputStream(file)
    val channel = input.getChannel
    try f(channel)
    finally input.close()
  }
  def fileOutputChannel[A](file: File)(f: FileChannel => A): A = {
    val output = new FileOutputStream(file)
    val channel = output.getChannel
    try f(channel)
    finally output.close()
  }


  def relativize(base: File, file: File): Option[String] =
    for {
      baseString <- baseFileString(base)
      pathString = file.getAbsolutePath
      if pathString startsWith baseString
    } yield pathString substring (baseString.length)

  private def baseFileString(baseFile: File): Option[String] =
    if(baseFile.isDirectory) {
      val cp = baseFile.getAbsolutePath
      assert(cp.length > 0)
      val normalized = if(cp.charAt(cp.length - 1) == File.separatorChar) cp else cp + File.separatorChar
      Some(normalized)
		} else None

  def allfiles(basedir: File): Seq[File] = {
    object files extends Traversable[File] {
      override def foreach[U](func: File => U): Unit = {
        def walk(current: File): Unit = current match {
           case null => ()
           case dir if dir.isDirectory => 
             func(dir)
             Option(dir.listFiles) foreach (list => list foreach walk)
           case f => func(f)
        }
        walk(basedir)
      }
    }
    files.toSeq
  }
}

