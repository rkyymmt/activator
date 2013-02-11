package snap

import org.junit.Assert._
import org.junit._

class TestValidation {

  @Test
  def testMap() {
    assertEquals(ProcessSuccess(10), ProcessSuccess(5) map (_ + 5))
    val err: ProcessResult[Int] = ProcessFailure("Failed!")
    assertEquals(err, err map (_ + 5))
  }

  @Test
  def testFlatMap() {
    assertEquals(ProcessSuccess(10), ProcessSuccess(5) flatMap (x => ProcessSuccess(x + 5)))
    val err: ProcessResult[Int] = ProcessFailure("Failed!")
    assertEquals(err, err flatMap (x => ProcessSuccess(x + 5)))
    assertEquals(err, ProcessSuccess(5) flatMap (x => err))
  }

  @Test
  def testZip() {
    val one = Validating(1)
    val two = Validating(2)
    val err: ProcessResult[Int] = ProcessFailure("err")
    val err2: ProcessResult[Int] = ProcessFailure("err2")

    assertTrue((err zip err2).isFailure)
    assertEquals(2, (err zip err2).asInstanceOf[ProcessFailure].failures.size)
    assertEquals(Validating(3), one zip two map { case (o, t) => o + t })
    assertEquals(err, one zip err)
    assertEquals(err, err zip one)
    assertEquals(err2, err2 zip two)
  }
  @Test
  def testValidation() {

    def check(c: ProcessResult[Int]) =
      c.validate(
        Validation("number is not odd")(_ % 2 == 1),
        Validation("number is less than 100")(_ < 100))

    assertEquals(Validating(1), check(Validating(1)))
    assertTrue(check(Validating(101)).isFailure)
    assertEquals(1, check(Validating(101)).asInstanceOf[ProcessFailure].failures.size)
    assertTrue(check(Validating(102)).isFailure)
    assertEquals(2, check(Validating(102)).asInstanceOf[ProcessFailure].failures.size)

  }
}