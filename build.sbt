import BuildHelper._
import scala.sys.process._

import scala.util.Try

val GITHUB_OWNER   = "conduktor"
val GITHUB_PROJECT = "zio-cache"
def env(v: String): Option[String] = sys.env.get(v)

inThisBuild(
  List(
    version := sys.env
      .getOrElse("RELEASE_VERSION", "0.0.1-SNAPSHOT"), // "RELEASE_VERSION" comes from.github/workflows/release.yml
    organization := "dev.zio",
    homepage     := Some(url("https://zio.github.io/zio-cache/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    publishTo := Some(
      s"Github $GITHUB_OWNER Apache Maven Packages of $GITHUB_PROJECT" at s"https://maven.pkg.github.com/$GITHUB_OWNER/$GITHUB_PROJECT"
    ),
    resolvers += s"GitHub $GITHUB_OWNER Apache Maven Packages" at s"https://maven.pkg.github.com/$GITHUB_OWNER/_/",
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    ),
    publishMavenStyle := true,
    credentials += Credentials(
      "GitHub Package Registry",
      "maven.pkg.github.com",
      GITHUB_OWNER,
      (env("GH_PACKAGES_TOKEN") orElse env("GH_READ_PACKAGES") orElse env("GITHUB_TOKEN"))
        .orElse(Try(s"git config github.token".!!).map(_.trim).toOption)
        .getOrElse(
          throw new RuntimeException(
            "Missing env variable: `GH_PACKAGES_TOKEN` or `GH_READ_PACKAGES` or `GITHUB_TOKEN` or git config option: `github.token`"
          )
        )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc")
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")
addCommandAlias("benchmark", "benchmarks/Jmh/run")

addCommandAlias(
  "testJVM",
  ";zioCacheJVM/test"
)
addCommandAlias(
  "testJS",
  ";zioCacheJS/test"
)
addCommandAlias(
  "testNative",
  ";zioCacheNative/test:compile"
)

val zioVersion = "1.0.12"

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )
  .aggregate(
    zioCacheJVM,
    zioCacheJS,
    zioCacheNative,
    benchmarks,
    docs
  )

lazy val zioCache = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-cache"))
  .settings(stdSettings("zio-cache"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.cache"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .enablePlugins(BuildInfoPlugin)

lazy val zioCacheJS = zioCache.js
  .settings(jsSettings)
  .settings(libraryDependencies += "dev.zio" %%% "zio-test-sbt" % zioVersion % Test)
  .settings(scalaJSUseMainModuleInitializer := true)

lazy val zioCacheJVM = zioCache.jvm
  .settings(dottySettings)
  .settings(libraryDependencies += "dev.zio" %%% "zio-test-sbt" % zioVersion % Test)
  .settings(scalaReflectTestSettings)

lazy val zioCacheNative = zioCache.native
  .settings(nativeSettings)

lazy val benchmarks = project
  .in(file("zio-cache-benchmarks"))
  .settings(stdSettings("zio-cache"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-cache-docs"
  )
  .dependsOn(zioCacheJVM)
  .enablePlugins(JmhPlugin)

lazy val docs = project
  .in(file("zio-cache-docs"))
  .settings(stdSettings("zio-cache"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-cache-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioCacheJVM),
    ScalaUnidoc / unidoc / target              := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite     := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(zioCacheJVM)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
