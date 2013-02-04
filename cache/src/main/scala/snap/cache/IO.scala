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
    if (dir.isDirectory) ()
    else if (dir.exists) sys.error(failBase + ": file exists and is not a directory.")
    else sys.error(failBase)
  }
  def copyFile(from: File, to: File): Unit = {
    require(from.exists, s"Source file '${from.getAbsolutePath}' does not exist.")
    require(!from.isDirectory, s"Source file '${from.getAbsolutePath}' is a directory.")
    fileInputChannel(from) { in =>
      fileOutputChannel(to) { out =>
        // maximum bytes per transfer according to  from http://dzone.com/snippets/java-filecopy-using-nio
        val max = (64 * 1024 * 1024) - (32 * 1024)
        val total = in.size
        def loop(offset: Long): Long =
          if (offset < total) loop(offset + out.transferFrom(in, offset, max))
          else offset
        val copied = loop(0)
        if (copied != in.size)
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
    if (baseFile.isDirectory) {
      val cp = baseFile.getAbsolutePath
      assert(cp.length > 0)
      val normalized = if (cp.charAt(cp.length - 1) == File.separatorChar) cp else cp + File.separatorChar
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
    files.toVector
  }

  /**
   * Creates a temporary directory and provides its location to the given function.  The directory
   * is deleted after the function returns.
   */
  def withTemporaryDirectory[T](action: File => T): T = {
    val dir = createTemporaryDirectory
    try { action(dir) }
    finally { delete(dir) }
  }
  /** The producer of randomness for unique name generation.*/
  private lazy val random = new java.util.Random
  lazy val temporaryDirectory = new File(System.getProperty("java.io.tmpdir"))

  /** Creates a directory in the default temporary directory with a name generated from a random integer. */
  def createTemporaryDirectory: File = createUniqueDirectory(temporaryDirectory)

  /** Creates a directory in `baseDirectory` with a name generated from a random integer */
  def createUniqueDirectory(baseDirectory: File): File = {
    def create(tries: Int): File =
      {
        if (tries > 3)
          sys.error("Could not create temporary directory.")
        else {
          val randomName = "sbt_" + java.lang.Integer.toHexString(random.nextInt)
          val f = new File(baseDirectory, randomName)

          try { createDirectory(f); f }
          catch { case e: Exception => create(tries + 1) }
        }
      }
    create(0)
  }

  /** Deletes each file or directory (recursively) in `files`.*/
  def delete(files: Iterable[File]): Unit = files.foreach(delete)

  /** Deletes each file or directory in `files` recursively.  Any empty parent directories are deleted, recursively.*/
  def deleteFilesEmptyDirs(files: Iterable[File]): Unit =
    {
      def isEmptyDirectory(dir: File) = dir.isDirectory && listFiles(dir).isEmpty
      def parents(fs: Set[File]) = fs.map(_.getParentFile)
      def deleteEmpty(dirs: Set[File]) {
        val empty = dirs filter isEmptyDirectory
        if (empty.nonEmpty) // looks funny, but this is true if at least one of `dirs` is an empty directory
        {
          empty foreach { _.delete() }
          deleteEmpty(parents(empty))
        }
      }

      delete(files)
      deleteEmpty(parents(files.toSet))
    }

  /** Deletes `file`, recursively if it is a directory. */
  def delete(file: File): Unit = {
    val deleted = file.delete()
    if (!deleted && file.isDirectory) {
      delete(listFiles(file))
      file.delete
    }
  }

  /** Returns the children of directory `dir` that match `filter` in a non-null array.*/
  def listFiles(filter: java.io.FileFilter)(dir: File): Array[File] = wrapNull(dir.listFiles(filter))

  /** Returns the children of directory `dir` that match `filter` in a non-null array.*/
  def listFiles(dir: File, filter: java.io.FileFilter): Array[File] = wrapNull(dir.listFiles(filter))

  /** Returns the children of directory `dir` in a non-null array.*/
  def listFiles(dir: File): Array[File] = wrapNull(dir.listFiles())

  def wrapNull(a: Array[File]) =
    if (a == null) new Array[File](0)
    else a

  /**
   * Loads a properties file into a java.util.Properties.
   * Note: May throw IOException.
   */
  def loadProperties(file: File): java.util.Properties = {
    val tmp = new java.util.Properties
    val input = new java.io.FileInputStream(file)
    try tmp load input
    finally input.close()
    tmp
  }

  def storeProperties(file: File, props: java.util.Properties) = {
    val output = new java.io.FileOutputStream(file)
    try props.store(output, "")
    finally output.close()
  }
}

