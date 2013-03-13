import sbt._
import Keys._
import SbtSupport.sbtLaunchJar

package sbt {
  object IvySbtCheater {
    def toID(m: ModuleID) = IvySbt toID m
  }
}

object Packaging {
  import com.typesafe.sbt.packager.Keys._
  import com.typesafe.sbt.SbtNativePackager._

  val repackagedLaunchJar = TaskKey[File]("repackaged-launch-jar", "The Builder launch jar.")
  val repackagedLaunchMappings = TaskKey[Seq[(File, String)]]("repackaged-launch-mappings", "New files for sbt-launch-jar")

  val scriptTemplateDirectory = SettingKey[File]("script-template-directory")
  val scriptTemplateOutputDirectory = SettingKey[File]("script-template-output-directory")
  val makeBashScript = TaskKey[File]("make-bash-script")
  val makeBatScript = TaskKey[File]("make-bat-script")


  val localTemplateSourceDirectory = SettingKey[File]("local-template-source-directory")
  val localTemplateCache = SettingKey[File]("local-template-cache")
  val localTemplateCacheCreated = TaskKey[File]("local-template-cache-created")
  
  val localRepoProjectsPublished = TaskKey[Unit]("local-repo-projects-published", "Ensures local projects are published before generating the local repo.")
  val localRepoArtifacts = SettingKey[Seq[ModuleID]]("local-repository-artifacts", "Artifacts included in the local repository.")
  val localRepoName = "install-to-local-repository"
  val localRepo = SettingKey[File]("local-repository", "The location to install a local repository.")
  val localRepoCreated = TaskKey[File]("local-repository-created", "Creates a local repository in the specified location.")
  
  // This is dirty, but play has stolen our keys, and we must mimc them here.
  val stage = TaskKey[Unit]("stage")
  val dist = TaskKey[File]("dist")
  
  // Shared settings to make a local repository.
  def makeLocalRepoSettings(lrepoName: String): Seq[Setting[_]] = Seq(
    localRepo <<= target(_ / "local-repository"),
    localRepoArtifacts := Seq.empty,
    resolvers in TheBuilderBuild.dontusemeresolvers <+= localRepo apply { f => Resolver.file(lrepoName, f)(Resolver.ivyStylePatterns) },
    localRepoProjectsPublished <<= (TheBuilderBuild.publishedProjects map (publishLocal in _)).dependOn,
    localRepoCreated <<= (localRepo, localRepoArtifacts, ivySbt in TheBuilderBuild.dontusemeresolvers, streams, localRepoProjectsPublished) map { (r, m, i, s, _) =>
      // TODO - Hook to detect if we need to recreate the repository....
      // That way we don't have to clean all the time.
      IvyHelper.createLocalRepository(m, lrepoName, i, s.log)
      r
    }
  )
  
  def settings: Seq[Setting[_]] = packagerSettings ++ useNativeZip ++ makeLocalRepoSettings(localRepoName) ++ Seq(
    name <<= version apply ("builder-" + _),
    wixConfig := <wix/>,
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageSummary := "Typesafe Builder",
    packageDescription := """A templating and project runner for Typesafe applications.""",
    stage <<= (target, mappings in Universal) map { (t, m) =>
      val to = t / "stage"
      val copies = m collect { case (f, p) => f -> (to / p) }
      IO.copy(copies)
      // Now set scripts to executable as a hack thanks to Java's lack of understanding of permissions
      (to / "builder").setExecutable(true, true)
    },
    dist <<= packageBin in Universal,
    mappings in Universal <+= (repackagedLaunchJar, version) map { (jar, v) =>
      jar -> ("builder-launch-%s.jar" format (v))
    },
    mappings in Universal <+= makeBashScript map (_ -> "builder"),
    mappings in Universal <+= makeBatScript map (_ -> "builder.bat"),
    mappings in Universal <++= localRepoCreated map { repo =>
      for {
        (file, path) <- (repo.*** --- repo) x relativeTo(repo)
      } yield file -> ("repository/" + path)
    },
    mappings in Universal <++= localTemplateCacheCreated map { repo =>
      for {
        (file, path) <- (repo.*** --- repo) x relativeTo(repo)
      } yield file -> ("templates/" + path)
    },
    rpmRelease := "1",
    rpmVendor := "typesafe",
    rpmUrl := Some("http://github.com/scala/scala-dist"),
    rpmLicense := Some("BSD"),

    repackagedLaunchJar <<= (target, sbtLaunchJar, repackagedLaunchMappings) map repackageJar,
    repackagedLaunchMappings := Seq.empty,
    repackagedLaunchMappings <+= (target, scalaVersion, version) map makeLauncherProps,

    scriptTemplateDirectory <<= (sourceDirectory) apply (_ / "templates"),
    scriptTemplateOutputDirectory <<= (target in Compile) apply (_ / "templates"),
    makeBashScript <<= (scriptTemplateDirectory, scriptTemplateOutputDirectory, version) map { (from, to, v) =>
      val template = from / "builder"
      val script = to / "builder"
      copyBashTemplate(template, script, v)
      script
    },
    makeBatScript <<= (scriptTemplateDirectory, scriptTemplateOutputDirectory, version) map { (from, to, v) =>
      val template = from / "builder.bat"
      val script = to / "builder.bat"
      copyBatTemplate(template, script, v)
      script
    },

    localTemplateSourceDirectory <<= (baseDirectory in ThisBuild) apply (_ / "templates"),
    localTemplateCache <<= target(_ / "template-cache"),
    localTemplateCacheCreated <<= (localTemplateSourceDirectory, localTemplateCache) map makeTemplateCache
  )

  def makeTemplateCache(sourceDir: File, targetDir: File): File = {
    IO createDirectory targetDir
    // TODO - we should be loading in the templates in this source and generating an index using the cache project's index generation main.
    // Or some such production-y thing.  For now, just copy some sh***
    IO.copyDirectory(sourceDir, targetDir)
    targetDir
  }


  // TODO - Use SBT caching API for this.
  def repackageJar(target: File, launcher: File, replacements: Seq[(File, String)] = Seq.empty): File = IO.withTemporaryDirectory { tmp =>
    val jardir = tmp / "jar"
    IO.createDirectory(jardir)
    IO.unzip(launcher, jardir)
    // TODO - manually delete sbt.boot.properties for james, since he's seeing wierd issues.
    (jardir ** "sbt.boot.properties0*").get map (f => IO delete f)

    // Copy new files
    val copys =
      for((file, path) <- replacements) 
      yield file -> (jardir / path)
    IO.copy(copys, overwrite=true, preserveLastModified=false)

    // Create new launcher jar    
    val tmplauncher = tmp / "builder-launcher.jar"
    val files = (jardir.*** --- jardir) x relativeTo(jardir)
    IO.zip(files, tmplauncher)
    
    // Put new launcher jar in new location.
    val nextlauncher = target / "builder-launcher.jar"
    if(nextlauncher.exists) IO.delete(nextlauncher)
    IO.move(tmplauncher, nextlauncher)
    nextlauncher
  }

  def copyBashTemplate(from: File, to: File, version: String): File = {
    val fileContents = IO read from
    val nextContents = fileContents.replaceAll("""\$\{\{template_declares\}\}""", 
                                               """|declare -r app_version="%s"
                                                  |""".stripMargin format (version))
    IO.write(to, nextContents)
    to setExecutable true
    to
  }
  def copyBatTemplate(from: File, to: File, version: String): File = {
    val fileContents = IO read from
    val nextContents = fileContents.replaceAll("""\$\{\{template_declares\}\}""",
                                               "set BUILDER_VERSION=%s" format (version))
    IO.write(to, nextContents)
    to setExecutable true
    to
  }

  // NOTE; Shares boot directory with SBT, good thing or bad?  not sure.
  // TODO - Just put this in the sbt-launch.jar itself!
  def makeLauncherProps(target: File, scalaVersion: String, version: String): (File, String) = {
    val tdir = target / "generated-sources"
    if(!tdir.exists) tdir.mkdirs()
    val tprops = tdir / (name + ".properties")
    // TODO - better caching
    // TODO - Add a local repository for resolving...
    if(!tprops.exists) IO.write(tprops, """
[scala]
  version: %s

[app]
  org: com.typesafe.builder
  name: builder-launcher
  version: %s
  class: builder.BuilderLauncher
  cross-versioned: false
  components: xsbti

[repositories]
  builder-local: file://${builder.local.repository-${builder.home-${user.home}/.builder}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: http://typesafe.artifactoryonline.com/typesafe/releases
  typesafe-ivy-releases: http://typesafe.artifactoryonline.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

[boot]
 directory: ${sbt.boot.directory-${sbt.global.base-${user.home}/.sbt}/boot/}

[ivy]
  ivy-home: ${user.home}/.ivy2
  checksums: ${sbt.checksums-sha1,md5}
  override-build-repos: ${sbt.override.build.repos-false}
""" format(scalaVersion, version))
    tprops -> "sbt/sbt.boot.properties"
  }
}
