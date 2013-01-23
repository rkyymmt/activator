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
        ProjectConfig(new File("foo")) +: old.projects
      old.copy(projects = projectList)
    }
    Await.ready(rewritten, 5 seconds)
    val c = RootConfig.user
    assertTrue("project 'foo' now in user config", c.projects.exists(_.location.getPath == "foo"))
  }
}
