import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.api.{Git => PGit}
import sbt._

class GitRepository(val repo: Repository) {
  val porcelain = new PGit(repo)

  def headCommit = Option(repo.resolve("HEAD")) map (_.name)
}
object jgit {
  /** Creates a new git instance from a base directory. */
  def apply(base: File) = new GitRepository({
    val gitDir = new File(base, ".git")
    new FileRepositoryBuilder().setGitDir(gitDir)
      .readEnvironment() // scan environment GIT_* variables
     .findGitDir() // scan up the file system tree
     .build()
  })  
}


