package snap.cache

import snap.IO
import java.io._
import org.junit.Assert._
import org.junit._
import activator.properties.ActivatorProperties._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

class ActionsTest {
  import scala.concurrent.ExecutionContext.Implicits.global
  class Dummy(dir: File) {
    // Setup dummies for test.
    val templateFile = new java.io.File(dir, "template-file")
    templateFile.createNewFile()
    val id = "1"
    val buildSbtFile = new File(dir, "template-build.sbt")
    IO.write(buildSbtFile, "\nname \t:= \t\"Hello\"\n")

    object DummyCache extends TemplateCache {
      val m = TemplateMetadata(
        IndexStoredTemplateMetadata(
          id = id,
          userConfig = AuthorDefinedTemplateMetadata(
            name = "",
            title = "",
            description = "",
            tags = Seq.empty),
          timeStamp = 1L,
          featured = true,
          usageCount = None),
        locallyCached = true)
      override val metadata = Future(Seq(m))
      override def template(id: String) =
        Future(Some(Template(m, Seq(
          templateFile -> "installed-file",
          dir -> "project",
          templateFile -> "project/build.properties",
          buildSbtFile -> "build.sbt"))))
      override def tutorial(id: String) = Future(None)
      override def search(query: String) = metadata
    }
    val installLocation = new java.io.File(dir, "template-install")

    assert(!installLocation.exists)
  }

  @Test
  def testCloneTemplate(): Unit = IO.withTemporaryDirectory { dir =>
    val dummy = new Dummy(dir)
    import dummy._

    // Run the command
    val result = Actions.cloneTemplate(DummyCache, id, installLocation, projectName = None)

    assert(!Await.result(result, Duration(5, SECONDS)).isFailure)

    // Now verify it worked!
    assert(installLocation.exists && installLocation.isDirectory)
    val installedFile = new File(installLocation, "installed-file")
    assert(installedFile.exists)
    // TODO - Check contents of the file, after we make the file.

    // Check that template ID was successfully written out.
    val props = IO loadProperties new File(installLocation, "project/build.properties")
    assert(props.getProperty(TEMPLATE_UUID_PROPERTY_NAME) == id)
  }

  @Test
  def testRenameProject(): Unit = IO.withTemporaryDirectory { dir =>
    val dummy = new Dummy(dir)
    import dummy._

    // this name needs escaping as regex, as string literal, etc.
    val newName = "test\"\"\" foo bar \"\"\" $1 $2 \n blah blah \\n \\ what"
    val result = Actions.cloneTemplate(DummyCache, id, installLocation, projectName = Some(newName))

    assert(!Await.result(result, Duration(5, SECONDS)).isFailure)

    val contents = IO.slurp(new File(installLocation, "build.sbt"))
    // see if this makes your head hurt
    assertEquals("\nname \t:= \t\"\"\"test\"\"\"+ \"\\\"\\\"\\\"\" + \"\"\" foo bar \"\"\"+ \"\\\"\\\"\\\"\" + \"\"\" $1 $2 \n blah blah \\n \\ what\"\"\"\n",
      contents)
  }
}
