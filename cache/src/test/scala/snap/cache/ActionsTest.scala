package snap.cache

import java.io._
import org.junit.Assert._
import org.junit._

class ActionsTest {
  @Test
  def testCloneTemplate(): Unit = IO.withTemporaryDirectory { dir =>
    // Setup dummies for test.
    val templateFile = new java.io.File(dir, "template-file")
    templateFile.createNewFile()
    val id = "1"
    object DummyCache extends TemplateCache {
      val m = TemplateMetadata(id, "", "", "", Seq.empty)
      override val metadata = Seq(m)
      override def template(id: String) =
        Some(Template(m, Seq(templateFile -> "installed-file")))
      override def tutorial(id: String) = None
      override def search(query: String): Iterable[TemplateMetadata] = metadata
    }
    val installLocation = new java.io.File(dir, "template-install")

    assert(!installLocation.exists)
    // Run the command
    Actions.cloneTemplate(DummyCache, id, installLocation)

    // Now verify it worked!
    assert(installLocation.exists && installLocation.isDirectory)
    val installedFile = new File(installLocation, "installed-file")
    assert(installedFile.exists)
    // TODO - Check contents of the file, after we make the file.
  }
}