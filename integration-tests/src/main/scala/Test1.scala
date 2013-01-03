// This test just makes sure our integration test framework itself works.
// If this fails, the build is all hosed in some fashion.
class DoesItWork extends snap.tests.IntegrationTest {
  // We don't do anything here.
  println("Integration tests work!")
}
