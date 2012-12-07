/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
import org.junit.Assert._
import org.junit._
import com.typesafe.sbtchild._
import com.typesafe.sbtchild.Protocol._
import java.io.File

class ChildTest {

  @Test
  def testTalkToChild(): Unit = {
    val child = SbtChild(new File("."))

    child.server.requestName()
    val name = child.server.receiveName()
    assertEquals("root", name)

    child.server.requestName()
    val name2 = child.server.receiveName()
    assertEquals("root", name2)
  }
}
