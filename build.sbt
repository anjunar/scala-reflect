import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

ThisBuild / version := "1.0.0"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.8.3"

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig ~= (
    _.withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
    )
)

lazy val scalaReflect = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
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

lazy val scalaReflectJS = scalaReflect.js
lazy val scalaReflectJVM = scalaReflect.jvm

lazy val root = (project in file("."))
  .aggregate(scalaReflectJS, scalaReflectJVM)
