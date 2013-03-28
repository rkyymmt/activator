package snap.cache

import snap.IO
import java.io._
import org.junit.Assert._
import org.junit._
import builder.properties.BuilderProperties._

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
        Some(Template(m, Seq(
          templateFile -> "installed-file",
          dir -> "project",
          templateFile -> "project/build.properties")))
      override def tutorial(id: String) = None
      Some(Template(m, Seq(
        templateFile -> "installed-file",
        dir -> "project",
        templateFile -> "project/build.properties")))
      override def search(query: String): Iterable[TemplateMetadata] = metadata
    }
    val installLocation = new java.io.File(dir, "template-install")

    assert(!installLocation.exists)
    // Run the command
    val result = Actions.cloneTemplate(DummyCache, id, installLocation)

    assert(!result.isFailure)

    // Now verify it worked!
    assert(installLocation.exists && installLocation.isDirectory)
    val installedFile = new File(installLocation, "installed-file")
    assert(installedFile.exists)
    // TODO - Check contents of the file, after we make the file.

    // Check that template ID was successfully written out.
    val props = IO loadProperties new File(installLocation, "project/build.properties")
    assert(props.getProperty(TEMPLATE_UUID_PROPERTY_NAME) == id)
  }
}