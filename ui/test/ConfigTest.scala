package test

import org.junit.Assert._
import org.junit._
import snap.RootConfig
import snap.AppConfig
import java.io.File
import scala.concurrent._
import scala.concurrent.duration._
import snap.properties.SnapProperties
import java.io.FileOutputStream

class ConfigTest {

  @Test
  def testUserConfig(): Unit = {
    val rewritten = RootConfig.rewriteUser { old =>
      val appList = if (old.applications.exists(_.location.getPath == "foo"))
        old.applications
      else
        AppConfig(new File("foo"), "id") +: old.applications
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("app 'foo' now in user config", c.applications.exists(_.location.getPath == "foo"))
  }

  def removeProjectName(): Unit = {
    val rewritten = RootConfig.rewriteUser { old =>
      val withNoName = old.applications
        .find(_.location.getPath == "foo")
        .getOrElse(AppConfig(new File("foo"), "id"))
        .copy(cachedName = None)

      val appList = withNoName +: old.applications.filter(_.location.getPath != "foo")
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("app 'foo' now in user config with no name",
      c.applications.exists({ p => p.location.getPath == "foo" && p.cachedName.isEmpty }))
  }

  @Test
  def testAddingProjectName(): Unit = {
    removeProjectName()

    val rewritten = RootConfig.rewriteUser { old =>
      val withName = old.applications
        .find(_.location.getPath == "foo")
        .getOrElse(AppConfig(new File("foo"), "id"))
        .copy(cachedName = Some("Hello World"))

      val appList = withName +: old.applications.filter(_.location.getPath != "foo")
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("app 'foo' now in user config with a name",
      c.applications.exists({ p => p.location.getPath == "foo" && p.cachedName == Some("Hello World") }))
  }

  @Test
  def testRecoveringFromBrokenFile(): Unit = {
    val file = new File(SnapProperties.SNAP_USER_HOME(), "config.json")
    try {
      file.delete()

      val stream = new FileOutputStream(file)
      stream.write("{ invalid json! ]".getBytes())
      stream.close()

      RootConfig.forceReload()

      val e = try {
        RootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json", e.getMessage().contains("was expecting double"))

      // delete the file... should now get one more error and then succeed
      file.delete()

      val e2 = try {
        RootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json", e2.getMessage().contains("was expecting double"))

      try {
        assertTrue("loaded an empty config after recovering from corrupt one", RootConfig.user.applications.isEmpty)
      } catch {
        case e: Exception =>
          throw new AssertionError("should not have had an error loading empty config", e)
      }
    } finally {
      // to avoid weird failures on next run of the tests
      file.delete()
    }
  }
}
