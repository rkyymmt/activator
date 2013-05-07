package snap

import java.io._
import java.nio.channels._
import java.nio.charset.Charset
import java.util.jar.{ Attributes, JarEntry, JarFile, JarInputStream, JarOutputStream, Manifest }
import java.util.zip.{ CRC32, GZIPOutputStream, ZipEntry, ZipFile, ZipInputStream, ZipOutputStream }
import scala.collection.immutable.TreeSet
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
    require(from.exists, "Source file '" + from.getAbsolutePath + "' does not exist.")
    require(!from.isDirectory, "Source file '" + from.getAbsolutePath + "' is a directory.")
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
          sys.error("Could not copy '" + from + "' to '" + to + "' (" + copied + "/" + in.size + " bytes copied)")
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
    files.toSeq
  }
  /**
   * Creates a file in the default temporary directory, calls `action` with the file, deletes the file, and returns the result of calling `action`.
   * The name of the file will begin with `prefix`, which must be at least three characters long, and end with `postfix`, which has no minimum length.
   */
  def withTemporaryFile[T](prefix: String, postfix: String)(action: File => T): T = {
    val file = File.createTempFile(prefix, postfix)
    try action(file)
    finally file.delete()
  }

  def move(a: File, b: File): Unit = {
    if (b.exists) delete(b)
    createDirectory(b.getParentFile)
    if (!a.renameTo(b)) {
      // TODO - Preserve last modified...
      copyFile(a, b)
      delete(a)
    }
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

  val utf8 = Charset.forName("UTF-8")
  def defaultCharset = utf8

  def slurp(file: File, charset: Charset = defaultCharset): String =
    reader(file, charset) { in =>
      val buf = new StringBuffer
      def read(): Unit = in.readLine match {
        case null => ()
        case line =>
          buf.append(line).append("\n")
          read()
      }
      read()
      buf.toString
    }

  def reader[T](file: File, charset: Charset)(f: BufferedReader => T): T = {
    val in = new java.io.FileInputStream(file)
    val reader = new java.io.InputStreamReader(in)
    val buf = new java.io.BufferedReader(reader)
    try f(buf)
    finally buf.close()
  }

  def write(file: File, content: String, charset: Charset = defaultCharset, append: Boolean = false): Unit =
    writer(file, content, charset, append) { _.write(content) }

  def writer[T](file: File, content: String, charset: Charset, append: Boolean = false)(f: BufferedWriter => T): T =
    if (charset.newEncoder.canEncode(content)) {
      // Annoying APIs, YEAH!
      val out = new java.io.FileOutputStream(file, append)
      val writer = new java.io.OutputStreamWriter(out, charset)
      val buf = new java.io.BufferedWriter(writer)
      try f(buf)
      finally {
        buf.close()
        // That should delegate to all the other closers.
      }
    } else sys.error("String cannot be encoded by charset " + charset.name)

  /**
   * Creates a zip file.
   * @param sources The files to include in the zip file paired with the entry name in the zip.  Only the pairs explicitly listed are included.
   * @param outputZip The file to write the zip to.
   */
  def zip(sources: Traversable[(File, String)], outputZip: File): Unit =
    archive(sources.toSeq, outputZip, None)

  private def archive(sources: Seq[(File, String)], outputFile: File, manifest: Option[Manifest]) {
    if (outputFile.isDirectory)
      error("Specified output file " + outputFile + " is a directory.")
    else {
      val outputDir = outputFile.getParentFile
      createDirectory(outputDir)
      withZipOutput(outputFile, manifest) { output =>
        val createEntry: (String => ZipEntry) = if (manifest.isDefined) new JarEntry(_) else new ZipEntry(_)
        writeZip(sources, output)(createEntry)
      }
    }
  }
  private def writeZip(sources: Seq[(File, String)], output: ZipOutputStream)(createEntry: String => ZipEntry) {
    val files = sources.flatMap { case (file, name) => if (file.isFile) (file, normalizeName(name)) :: Nil else Nil }

    val now = System.currentTimeMillis
    // The CRC32 for an empty value, needed to store directories in zip files
    val emptyCRC = new CRC32().getValue()

    def addDirectoryEntry(name: String) {
      output putNextEntry makeDirectoryEntry(name)
      output.closeEntry()
    }

    def makeDirectoryEntry(name: String) =
      {
        //			log.debug("\tAdding directory " + relativePath + " ...")
        val e = createEntry(name)
        e setTime now
        e setSize 0
        e setMethod ZipEntry.STORED
        e setCrc emptyCRC
        e
      }

    def makeFileEntry(file: File, name: String) =
      {
        //			log.debug("\tAdding " + file + " as " + name + " ...")
        val e = createEntry(name)
        e setTime file.lastModified
        e
      }
    def addFileEntry(file: File, name: String) {
      output putNextEntry makeFileEntry(file, name)
      transfer(file, output)
      output.closeEntry()
    }

    //Calculate directories and add them to the generated Zip
    allDirectoryPaths(files) foreach addDirectoryEntry

    //Add all files to the generated Zip
    files foreach { case (file, name) => addFileEntry(file, name) }
  }
  private def withZipOutput(file: File, manifest: Option[Manifest])(f: ZipOutputStream => Unit) {
    Using.fileOutputStream(false)(file) { fileOut =>
      val (zipOut, ext) =
        manifest match {
          case Some(mf) =>
            import Attributes.Name.MANIFEST_VERSION
            val main = mf.getMainAttributes
            if (!main.containsKey(MANIFEST_VERSION))
              main.put(MANIFEST_VERSION, "1.0")
            (new JarOutputStream(fileOut, mf), "jar")
          case None => (new ZipOutputStream(fileOut), "zip")
        }
      try f(zipOut)
      finally zipOut.close
    }
  }
  private def normalizeName(name: String) = {
    val sep = File.separatorChar
    if (sep == '/') name else name.replace(sep, '/')
  }

  // produce a sorted list of all the subdirectories of all provided files
  private def allDirectoryPaths(files: Iterable[(File, String)]) =
    TreeSet[String]() ++ (files flatMap { case (file, name) => directoryPaths(name) })

  // map a path a/b/c to List("a/b/", "a/")
  private def directoryPaths(path: String): List[String] =
    directories(relativeComponents(path)).filter(_.length > 1)

  // map a path a/b/c to List("a", "b")
  private def relativeComponents(path: String): List[String] =
    path.split("/").toList.dropRight(1)
  // map components List("a", "b", "c") to List("a/b/c/", "a/b/", "a/", "")
  private def directories(path: List[String]): List[String] =
    path.foldLeft(List(""))((e, l) => (e.head + l + "/") :: e)

  /**
   * Copies the contents of the input file `in` to the `out` stream.
   * The output stream is not closed by this method.
   */
  def transfer(in: File, out: OutputStream): Unit =
    Using.fileInputStream(in) { in => transfer(in, out) }

  /**
   * Copies all bytes from the given input stream to the given output stream.
   * Neither stream is closed.
   */
  def transfer(in: InputStream, out: OutputStream): Unit = transferImpl(in, out, false)

  private def transferImpl(in: InputStream, out: OutputStream, close: Boolean) {
    try {
      val buffer = new Array[Byte](8192)
      def read(): Unit = {
        val byteCount = in.read(buffer)
        if (byteCount >= 0) {
          out.write(buffer, 0, byteCount)
          read()
        }
      }
      read()
    } finally { if (close) in.close }
  }
}

abstract class Using[Source, T] {
  protected def open(src: Source): T
  def apply[R](src: Source)(f: T => R): R =
    {
      val resource = open(src)
      try { f(resource) }
      finally { close(resource) }
    }
  protected def close(out: T): Unit
}
trait OpenFile[T] extends Using[File, T] {
  protected def openImpl(file: File): T
  protected final def open(file: File): T =
    {
      val parent = file.getParentFile
      if (parent != null)
        IO.createDirectory(parent)
      openImpl(file)
    }
}
object Using {
  def file[T <: Closeable](openF: File => T): OpenFile[T] = file(openF, closeCloseable)
  def file[T](openF: File => T, closeF: T => Unit): OpenFile[T] =
    new OpenFile[T] {
      def openImpl(file: File) = openF(file)
      def close(t: T) = closeF(t)
    }
  private def closeCloseable[T <: Closeable]: T => Unit = _.close()
  def fileInputStream = file(f => new BufferedInputStream(new FileInputStream(f)))
  def fileOutputStream(append: Boolean = false) = file(f => new BufferedOutputStream(new FileOutputStream(f, append)))
}
