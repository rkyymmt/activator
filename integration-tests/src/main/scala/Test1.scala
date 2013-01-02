class Foo extends xsbti.AppMain {

  def run(configuration: xsbti.AppConfiguration) = {
   System.err.println("TEST ERROR OUTPUT")
   Exit(0)
  }
  

  case class Exit(code: Int) extends xsbti.Exit
}
