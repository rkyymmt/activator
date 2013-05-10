import sbt._
import Keys._
import SbtSupport.{sbtLaunchJar,jansiJar}

package sbt {
  object IvySbtCheater {
    def toID(m: ModuleID) = IvySbt toID m
  }
}


case class LocalRepoReport(location: File, licenses: Seq[License])

object Packaging {
  import com.typesafe.sbt.packager.Keys._
  import com.typesafe.sbt.SbtNativePackager._

  val repackagedLaunchJar = TaskKey[File]("repackaged-launch-jar", "The repackaged launch jar for this product.")
  val repackagedLaunchMappings = TaskKey[Seq[(File, String)]]("repackaged-launch-mappings", "New files for sbt-launch-jar")

  // TODO - rename this to just template directory...
  val scriptTemplateDirectory = SettingKey[File]("script-template-directory")
  val scriptTemplateOutputDirectory = SettingKey[File]("script-template-output-directory")
  val makeBashScript = TaskKey[File]("make-bash-script")
  val makeBatScript = TaskKey[File]("make-bat-script")

  val licenseFileUrl = SettingKey[String]("activator-license-url")
  val licenseFileLocation = SettingKey[File]("activator-license-location")
  val licenseFileDownloaded = TaskKey[File]("activator-license-downloaded")
  val makeReadmeHtml = TaskKey[File]("make-readme-html")
  val makeLicensesHtml = TaskKey[File]("make-licenses-html")

  val localRepoProjectsPublished = TaskKey[Unit]("local-repo-projects-published", "Ensures local projects are published before generating the local repo.")
  val localRepoArtifacts = SettingKey[Seq[ModuleID]]("local-repository-artifacts", "Artifacts included in the local repository.")
  val localRepoName = "install-to-local-repository"
  val localRepo = SettingKey[File]("local-repository", "The location to install a local repository.")
  val localRepoCreation = TaskKey[LocalRepoReport]("local-repository-creation", "Creates a local repository in the specified location.")
  val localRepoLicenses = TaskKey[Unit]("local-repository-licenses", "Prints all the licenses used by software in the local repo.")
  val localRepoCreated = TaskKey[File]("local-repository-created", "Creates a local repository in the specified location.")
  
  // This is dirty, but play has stolen our keys, and we must mimc them here.
  val stage = TaskKey[Unit]("stage")
  val dist = TaskKey[File]("dist")
  
  // Shared settings to make a local repository.
  def makeLocalRepoSettings(lrepoName: String): Seq[Setting[_]] = Seq(
    localRepo <<= target(_ / "local-repository"),
    localRepoArtifacts := Seq.empty,
    resolvers in TheActivatorBuild.dontusemeresolvers <+= localRepo apply { f => Resolver.file(lrepoName, f)(Resolver.ivyStylePatterns) },
    localRepoProjectsPublished <<= (TheActivatorBuild.publishedProjects map (publishLocal in _)).dependOn,
    localRepoCreation <<= (localRepo, localRepoArtifacts, ivySbt in TheActivatorBuild.dontusemeresolvers, streams, localRepoProjectsPublished) map { (r, m, i, s, _) =>
      val licenses = IvyHelper.createLocalRepository(m, lrepoName, i, s.log)
      LocalRepoReport(r, licenses)
    },
    localRepoCreated <<= localRepoCreation map (_.location),
    localRepoLicenses <<= (localRepoCreation, streams) map { (config, s) =>
      // Stylize the licenses we used and give an inline report...
      s.log.info("--- Licenses ---")
      val badList = Set("and", "the", "license", "revised")
      def makeSortString(in: String): String =
        in split ("\\s+") map (_.toLowerCase) filterNot badList mkString ""
      for(license <- config.licenses sortBy (l => makeSortString(l.name))) {
        s.log.info(" * " + license.name + " @ " + license.url)
         s.log.info("    - " + license.deps.mkString(", "))
      }
    }
  )
  
  def settings: Seq[Setting[_]] = packagerSettings ++ useNativeZip ++ makeLocalRepoSettings(localRepoName) ++ Seq(
    name <<= version apply ("activator-" + _),
    wixConfig := <wix/>,
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageSummary := "Activator",
    packageDescription := """Helps developers get started with Typesafe technologies quickly and easily.""",
    stage <<= (target, mappings in Universal) map { (t, m) =>
      val to = t / "stage"
      val copies = m collect { case (f, p) => f -> (to / p) }
      IO.copy(copies)
      // Now set scripts to executable as a hack thanks to Java's lack of understanding of permissions
      (to / "activator").setExecutable(true, true)
    },
    dist <<= packageBin in Universal,
    mappings in Universal <+= (repackagedLaunchJar, version) map { (jar, v) =>
      jar -> ("activator-launch-%s.jar" format (v))
    },
    mappings in Universal <+= makeBashScript map (_ -> "activator"),
    mappings in Universal <+= makeBatScript map (_ -> "activator.bat"),
    mappings in Universal <+= makeReadmeHtml map (_ -> "README.html"),
    mappings in Universal <+= makeLicensesHtml map (_ -> "LICENSE.html"),
    mappings in Universal <+= licenseFileDownloaded map (file => file -> file.getName),
    mappings in Universal <++= localRepoCreated map { repo =>
      for {
        (file, path) <- (repo.*** --- repo) x relativeTo(repo)
      } yield file -> ("repository/" + path)
    },
    mappings in Universal <++= (LocalTemplateRepo.localTemplateCacheCreated in TheActivatorBuild.localTemplateRepo) map { repo =>
      for {
        (file, path) <- (repo.*** --- repo) x relativeTo(repo)
      } yield file -> ("templates/" + path)
    },
    rpmRelease := "1",
    rpmVendor := "typesafe",
    rpmUrl := Some("http://github.com/scala/scala-dist"),
    rpmLicense := Some("BSD"),

    repackagedLaunchJar <<= (target, sbtLaunchJar, jansiJar, repackagedLaunchMappings) map repackageJar,
    repackagedLaunchMappings := Seq.empty,
    repackagedLaunchMappings <+= (target, scalaVersion, version) map makeLauncherProps,

    scriptTemplateDirectory <<= (sourceDirectory) apply (_ / "templates"),
    scriptTemplateOutputDirectory <<= (target in Compile) apply (_ / "templates"),
    makeBashScript <<= (scriptTemplateDirectory, scriptTemplateOutputDirectory, version) map { (from, to, v) =>
      val template = from / "activator"
      val script = to / "activator"
      copyBashTemplate(template, script, v)
      script
    },
    makeBatScript <<= (scriptTemplateDirectory, scriptTemplateOutputDirectory, version) map { (from, to, v) =>
      val template = from / "activator.bat"
      val script = to / "activator.bat"
      copyBatTemplate(template, script, v)
      script
    },
    makeReadmeHtml <<= (scriptTemplateDirectory, scriptTemplateOutputDirectory, version) map { (from, to, v) =>
      val template = from / "README.md"
      val output = to / "README.html"
      Markdown.makeHtml(template, output, title="Activator")
      output
    },
    makeLicensesHtml <<= (scriptTemplateDirectory, scriptTemplateOutputDirectory, version) map { (from, to, v) =>
      val template = from / "LICENSE.md"
      val output = to / "LICENSE.html"
      Markdown.makeHtml(template, output, title="Activator License")
      output
    },
    licenseFileUrl := "http://typesafe.com/public/legal/TypesafeSubscriptionAgreement-v1.pdf",
    licenseFileLocation <<= (licenseFileUrl, target) apply { (url, tdir) =>
      val asUrl = new java.net.URL(url)
      // Grab File Name...
      val fileName = asUrl.getPath.split("/").lastOption getOrElse sys.error("Bad License URL: " + asUrl)
      tdir / fileName
    },
    licenseFileDownloaded <<= (licenseFileUrl, licenseFileLocation) map { (urlString, file) =>
      if(!file.exists) {
        IO.download(new java.net.URL(urlString), file)
      } 
      file
    }
  )
  

  // TODO - Use SBT caching API for this.
  def repackageJar(target: File, launcher: File, jansi: File, replacements: Seq[(File, String)] = Seq.empty): File = IO.withTemporaryDirectory { tmp =>
    val jardir = tmp / "jar"
    IO.createDirectory(jardir)
    // Explode jansi into the jar directory first.
    IO.unzip(jansi, jardir)
    IO.unzip(launcher, jardir)
    // TODO - manually delete sbt.boot.properties for james, since he's seeing wierd issues.
    (jardir ** "sbt.boot.properties0*").get map (f => IO delete f)

    // Copy new files
    val copys =
      for((file, path) <- replacements) 
      yield file -> (jardir / path)
    IO.copy(copys, overwrite=true, preserveLastModified=false)

    // Create new launcher jar    
    val tmplauncher = tmp / "activator-launcher.jar"
    val files = (jardir.*** --- jardir) x relativeTo(jardir)
    IO.zip(files, tmplauncher)
    
    // Put new launcher jar in new location.
    val nextlauncher = target / "activator-launcher.jar"
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
                                               "set APP_VERSION=%s" format (version))
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
  org: com.typesafe.activator
  name: activator-launcher
  version: %s
  class: activator.ActivatorLauncher
  cross-versioned: false
  components: xsbti

[repositories]
  activator-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
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
