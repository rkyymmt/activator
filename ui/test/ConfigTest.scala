package test

import org.junit.Assert._
import org.junit._
import snap.RootConfig
import snap.AppConfig
import java.io.File
import scala.concurrent._
import scala.concurrent.duration._

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
}
