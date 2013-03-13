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

  val testUtil = new com.typesafe.sbtchild.TestUtil(scratchDir = new File("ui/target/scratch"))

  import testUtil._

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

  private def routeExpectingAnError[B](req: FakeRequest[_], body: B)(implicit w: Writeable[B]): String = {
    route(req, body) map deAsync match {
      case Some(result: SimpleResult[_]) if result.header.status != Status.OK => contentAsString(result)
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

  // we are supposed to fail to "import" an empty directory
  @Test
  def testHandleEmptyDirectory(): Unit = {
    val dummy = makeDummyEmptyDirectory("notAnSbtProject")

    running(FakeApplication()) {
      val uri = (new URI("/api/app/fromLocation")).addQueryParameter("location", dummy.getPath)

      val message =
        routeExpectingAnError(FakeRequest(method = "POST", uri = uri.getRawPath + "?" + uri.getRawQuery,
          headers = FakeHeaders(Seq.empty), body = ""), "")

      if (!message.contains("Directory does not contain an sbt build"))
        throw new AssertionError(s"Got wrong error message: '$message'")
    }
  }

  private def childTest(projectMaker: String => File, projectName: String)(assertions: JsValue => Unit): Unit = {
    val dummy = projectMaker(projectName)

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
        "taskId" -> JsString("test-" + projectName + "-task-id"),
        "description" -> JsString(projectName + " Test"),
        "task" -> JsObject(Seq("type" -> JsString("GenericRequest"), "name" -> JsString("run")))))

      val runReq = FakeRequest(method = "POST", uri = "/api/sbt/task", body = AnyContentAsJson(runJson),
        headers = FakeHeaders(Seq(
          HeaderNames.CONTENT_TYPE -> Seq("application/json; charset=utf-8"))))

      val taskJson = routeThrowingIfNotJson(runReq, runJson)

      assertions(taskJson)
    }
  }

  @Test
  def testRunChild(): Unit = {
    childTest(makeDummySbtProject, "runChild") { taskJson =>
      assertEquals(JsString("RequestReceivedEvent"), taskJson \ "type")
      // TODO somehow we need to test that the websocket gets a RunReponse
    }
  }

  @Test
  def testRunChildBrokenBuild(): Unit = {
    childTest(makeDummySbtProjectWithBrokenBuild, "runChildBrokenBuild") { taskJson =>
      assertEquals(JsString("ErrorResponse"), taskJson \ "type")
      assertEquals(JsString("sbt process never got in touch, so unable to handle request GenericRequest(true,run,Map())"), taskJson \ "error")
    }
  }

  @Test
  def testRunChildMissingMain(): Unit = {
    childTest(makeDummySbtProjectWithNoMain, "runChildMissingMain") { taskJson =>
      assertEquals(JsString("RequestReceivedEvent"), taskJson \ "type")
      // TODO somehow we need to test that the websocket gets an ErrorResponse
    }
  }

}
