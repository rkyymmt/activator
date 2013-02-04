/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
package test

import org.junit.Assert._
import org.junit._
import java.io.File
import play.api.test._
import play.api.libs.json._
import play.api.test.Helpers._
import java.net.URI
import snap.EnhancedURI._
import language.implicitConversions
import play.api.mvc._
import play.api.http._
import scala.concurrent.Await
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import play.api.libs.iteratee._

class SbtTest {

  def createFile(name: java.io.File, content: String): Unit = {
    val writer = new java.io.FileWriter(name)
    try writer.write(content)
    finally writer.close()
  }

  /** Creates a dummy project we can run sbt against. */
  def makeDummySbtProject(relativeDir: String): File = {
    val dir = new File(new File("ui/target/scratch"), relativeDir)
    if (!dir.isDirectory()) dir.mkdirs()

    val project = new File(dir, "project")
    if (!project.isDirectory()) project.mkdirs()

    val props = new File(project, "build.properties")
    createFile(props, "sbt.version=" + snap.properties.SnapProperties.SBT_VERSION)

    val build = new File(dir, "build.sbt")
    createFile(build, "name := \"" + relativeDir + "\"\n")

    val scalaSource = new File(dir, "src/main/scala")
    if (!scalaSource.isDirectory()) scalaSource.mkdirs()
    val main = new File(scalaSource, "hello.scala")
    createFile(main, "object Main extends App { println(\"Hello World\") }\n")
    dir
  }

  private def deAsync(result: Result): Result = result match {
    case AsyncResult(p) => {
      implicit val timeout = Timeout(120, TimeUnit.SECONDS)
      deAsync(Await.result(p, timeout.duration))
    }
    case whatever => whatever
  }

  // the "body" and "Writeable" args are a workaround for
  // https://play.lighthouseapp.com/projects/82401/tickets/770-fakerequestwithjsonbody-no-longer-works
  // TODO drop this hack when upgrading past Play 2.1-RC1
  private def routeThrowingIfNotSuccess[B](req: FakeRequest[_], body: B)(implicit w: Writeable[B]): SimpleResult[_] = {
    route(req, body) map deAsync match {
      case Some(result: SimpleResult[_]) if result.header.status == Status.OK => result
      case Some(whatever) =>
        val message = try contentAsString(whatever)
        catch { case e: Exception => "" }
        throw new RuntimeException("unexpected result: " + whatever + ": " + message)
      case None =>
        throw new RuntimeException("got None back from request: " + req)
    }
  }

  private def routeThrowingIfNotJson[B](req: FakeRequest[_], body: B)(implicit w: Writeable[B]): JsValue = {
    val result = routeThrowingIfNotSuccess(req, body)
    if (contentType(result) != Some("application/json"))
      throw new RuntimeException("Wrong content type: " + contentType(result))
    Json.parse(contentAsString(result))
  }

  @Test
  def testRunChild(): Unit = {
    val dummy = makeDummySbtProject("runChild")

    running(FakeApplication()) {
      val uri = (new URI("/api/app/fromLocation")).addQueryParameter("location", dummy.getPath)

      val createJson =
        routeThrowingIfNotJson(FakeRequest(method = "POST", uri = uri.getRawPath + "?" + uri.getRawQuery,
          headers = FakeHeaders(Seq.empty), body = ""), "")

      val appId = createJson match {
        case o: JsObject => o \ "id" match {
          case JsString(s) => s
          case whatever => throw new RuntimeException("id not found, found: " + whatever)
        }
        case whatever => throw new RuntimeException("bad result, got: " + whatever)
      }

      val runJson = JsObject(Seq("appId" -> JsString(appId),
        "description" -> JsString("Run Child Test"),
        "task" -> JsObject(Seq("type" -> JsString("RunRequest")))))

      val runReq = FakeRequest(method = "POST", uri = "/api/sbt/task", body = AnyContentAsJson(runJson),
        headers = FakeHeaders(Seq(
          HeaderNames.CONTENT_TYPE -> Seq("application/json; charset=utf-8"))))

      val taskJson = routeThrowingIfNotJson(runReq, runJson)

      assertEquals(JsString("RunResponse"), taskJson \ "type")
    }
  }
}
