package snap

import org.junit._

import java.io.File
class PlatformTest {
  val Windows = new Platform(true)
  val Linux = new Platform(false)

  @Test
  def transformIsReversable(): Unit = {
    def testReversal(name: String, platform: Platform, names: Seq[String]): Unit = {
      val files = names map (new File(_))
      val translated = files map platform.getClientFriendlyFilename
      val files2 = names map platform.fromClientFriendlyFilename
      Assert.assertTrue(s"failed to canonically convert $name files: ", files zip files2 forall {
        case (l, r) => l.getAbsolutePath == r.getAbsolutePath
      })
    }
    // TODO - Find more odd file paths to test here...
    testReversal("Windows", Windows, Seq(
      "C:\\Users\\Josh\\Fun",
      "C:\\Program Files (x86)\\SNAPSTER\\I HATE WINDOWS"))
    testReversal("Linux", Linux, Seq(
      "/home/jsuereth/projects/stuff with spaces/guy",
      "/home/jsuereth/projects/regular"))
  }
}