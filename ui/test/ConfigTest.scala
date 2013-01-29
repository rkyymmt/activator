package test

import org.junit.Assert._
import org.junit._
import snap.RootConfig
import snap.ProjectConfig
import java.io.File
import scala.concurrent._
import scala.concurrent.duration._

class ConfigTest {

  @Test
  def testUserConfig(): Unit = {
    val rewritten = RootConfig.rewriteUser { old =>
      val projectList = if (old.projects.exists(_.location.getPath == "foo"))
        old.projects
      else
        ProjectConfig(new File("foo"), "id") +: old.projects
      old.copy(projects = projectList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("project 'foo' now in user config", c.projects.exists(_.location.getPath == "foo"))
  }

  def removeProjectName(): Unit = {
    val rewritten = RootConfig.rewriteUser { old =>
      val withNoName = old.projects
        .find(_.location.getPath == "foo")
        .getOrElse(ProjectConfig(new File("foo"), "id"))
        .copy(cachedName = None)

      val projectList = withNoName +: old.projects.filter(_.location.getPath != "foo")
      old.copy(projects = projectList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("project 'foo' now in user config with no name",
      c.projects.exists({ p => p.location.getPath == "foo" && p.cachedName.isEmpty }))
  }

  @Test
  def testAddingProjectName(): Unit = {
    removeProjectName()

    val rewritten = RootConfig.rewriteUser { old =>
      val withName = old.projects
        .find(_.location.getPath == "foo")
        .getOrElse(ProjectConfig(new File("foo"), "id"))
        .copy(cachedName = Some("Hello World"))

      val projectList = withName +: old.projects.filter(_.location.getPath != "foo")
      old.copy(projects = projectList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("project 'foo' now in user config with a name",
      c.projects.exists({ p => p.location.getPath == "foo" && p.cachedName == Some("Hello World") }))
  }
}
