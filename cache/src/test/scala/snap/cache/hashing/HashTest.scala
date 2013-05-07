package snap
package cache
package hashing

import org.junit.Assert._
import org.junit._
import java.io.File

class HashTest {

  @Test
  def fileHashesShouldBeTheSame(): Unit = {
    import Hash.default._
    val temp = File.createTempFile("test", "test hash")
    IO.write(temp, "Here is some content that we plan to hash!")
    assertEquals("Files should has the same!", hash(temp), hash(temp))
  }
}
