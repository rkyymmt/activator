package snap

import org.specs2.mutable.Specification

object TestValidation extends Specification {

  "A ProcessResult" should {
    val err: ProcessResult[Int] = ProcessFailure("Failed!")
    val err2: ProcessResult[Int] = ProcessFailure("Failed Two!")
    val one = Validating(1)
    val two = Validating(2)

    "successfully map success" in {
      ProcessSuccess(5) map (_ + 5) must equalTo(ProcessSuccess(10))
    }
    "do nothing when mapping a failure" in {
      err map (_ + 5) must equalTo(err)
    }
    "successfull chain success with flatMap" in {
      ProcessSuccess(5) flatMap (x => ProcessSuccess(x + 5)) must equalTo(ProcessSuccess(10))
    }
    "do nothing when chaining an error with flatMap" in {
      err flatMap (x => ProcessSuccess(x + 5)) must equalTo(err)
    }
    "Drop results when chaining to an error in flatMap" in {
      ProcessSuccess(5) flatMap (x => err) must equalTo(err)
    }

    "Successfully zip with another result" in {
      one zip two must equalTo(Validating(1 -> 2))
    }
    "Successfully zip together errors" in {
      err zip err2 must equalTo(ProcessFailure(Seq[ProcessError]("Failed!", "Failed Two!")))
    }
    "Take an error when zipping error and success" in {
      err zip one must equalTo(err)
      one zip err must equalTo(err)
    }
  }

  "A processResult validate call" should {
    val err1 = "number is not odd"
    val err2 = "number is not less than 100"
    def check(c: ProcessResult[Int]) =
      c.validate(
        Validation(err1)(_ % 2 == 1),
        Validation(err2)(_ < 100))
    "Join together failures" in {
      check(Validating(102)) must equalTo(ProcessFailure(Seq[ProcessError](err1, err2)))
    }
    "Only report valid errors" in {
      check(Validating(101)) must equalTo(ProcessFailure(Seq[ProcessError](err2)))
      check(Validating(2)) must equalTo(ProcessFailure(Seq[ProcessError](err1)))
    }
    "Allow successful valdiations through" in {
      check(Validating(3)) must equalTo(Validating(3))
    }
  }

  "Validating convenience" should {
    "Catch errors as failures" in {
      Validating(sys.error("O NOES")).isFailure must beTrue
    }
    "Attach custom messages" in {
      val msg =
        Validating.withMsg("Hello, World")(sys.error("O NOES")) match {
          case ProcessFailure(Seq(err)) => err.msg
          case _ => ""
        }
      msg must equalTo("Hello, World")
    }
  }

  "Default validations" should {
    "detect non empty strings" in {
      Validating("Hello").validate(Validation.nonEmptyString("O NOES")) must equalTo(Validating("Hello"))
      Validating("").validate(Validation.nonEmptyString("O NOES")).isFailure must beTrue
    }
    "detect non empty collections" in {
      Validating(Seq(1, 2)).validate(Validation.nonEmptyCollection("A")).isFailure must beFalse
      Validating(Seq.empty[Int]).validate(Validation.nonEmptyCollection("A")).isFailure must beTrue
    }
  }
}
