package launcher

import concurrent.duration._
import concurrent.Await
import concurrent.ExecutionContext.Implicits.global
import concurrent.future
import snap.tests._

class MustStartUI extends IntegrationTest {
  
  val sbtProject = makeDummySbtProject(new java.io.File("dummy"))
  
  val process = run_snap(Seq("ui"), sbtProject).run
  
  // Wait for Http Server startup on port 8888
  // TODO - If we pick a random port in the future, this needs to detect it...
  try assert(waitForHttpServerStartup("http://localhost:8888/"))
  finally {
    process.destroy()
  }
}