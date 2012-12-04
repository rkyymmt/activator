/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
import org.junit.Assert._
import org.junit._
import com.typesafe.sbtchild._
import com.typesafe.sbtchild.Protocol._

class ChildTest {

  @Test
  def testTalkToChild(): Unit = {
    val child = SbtChild()

    child.server.requestName()
    val name = child.server.receiveName()
    println("name is " + name)
    assertEquals("foobar", name)

    child.server.requestName()
    val name2 = child.server.receiveName()
    println("name2 is " + name2)
    assertEquals("foobar", name2)
  }
}
