import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import scalajscrossproject.JSPlatform
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import sbtcrossproject.JVMPlatform

ThisBuild / version := "1.1.2"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / homepage := Some(url("https://github.com/anjunar/scala-reflect"))
ThisBuild / description := "Compile-time reflection for Scala on JVM and Scala.js."
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/scala-reflect"),
    "scm:git:git@github.com:anjunar/scala-reflect.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "anjunar",
    name = "Anjunar",
    email = "",
    url = url("https://github.com/anjunar")
  )
)
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig ~= (
    _.withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
    )
)

lazy val scalaReflect = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .configurePlatforms(JSPlatform)(_.withId("scala-reflect-js"))
  .configurePlatforms(JVMPlatform)(_.withId("scala-reflect-jvm"))
  .settings(
    name := "scala-reflect",
    moduleName := "scala-reflect"
  )
  .jsSettings(commonJsSettings)
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

lazy val scalaReflectJs = scalaReflect.js
lazy val scalaReflectJvm = scalaReflect.jvm

lazy val root = Project(id = "scala-reflect-root", base = file("."))
  .aggregate(scalaReflectJs, scalaReflectJvm)
  .settings(
    name := "scala-reflect",
    moduleName := "scala-reflect",
    publish / skip := true
  )
