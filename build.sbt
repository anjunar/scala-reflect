import org.scalajs.linker.interface.{ESVersion, ModuleKind}

lazy val scala3 = "3.3.8"
lazy val rootDir = file(".").getAbsoluteFile

version := "1.1.3"
organization := "com.anjunar"
organizationName := "Anjunar"
organizationHomepage := Some(url("https://github.com/anjunar"))
scalaVersion := scala3
versionScheme := Some("early-semver")
homepage := Some(url("https://github.com/anjunar/scala-reflect"))
description := "Compile-time reflection for Scala on JVM and Scala.js."
licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/scala-reflect"),
    "scm:git:git@github.com:anjunar/scala-reflect.git"
  )
)

developers := List(
  Developer(
    id = "anjunar",
    name = "Anjunar",
    email = "",
    url = url("https://github.com/anjunar")
  )
)

pomIncludeRepository := { _ => false }
publishMavenStyle := true

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

def platformDir(axes: Seq[VirtualAxis]): String =
  if (axes.contains(VirtualAxis.js)) "js"
  else if (axes.contains(VirtualAxis.jvm)) "jvm"
  else sys.error(s"Unsupported platform axes: $axes")

def crossDirs(configuration: String, kind: String) = Def.setting {
  val platform = platformDir(virtualAxes.value)

  Seq(
    rootDir / "shared" / "src" / configuration / kind,
    rootDir / platform / "src" / configuration / kind
  )
}

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig ~= (
    _.withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
    )
)

lazy val commonCrossSettings = Seq(
  name := "scala-reflect",
  moduleName := "scala-reflect",

  Compile / unmanagedSourceDirectories := crossDirs("main", "scala").value,
  Test / unmanagedSourceDirectories := crossDirs("test", "scala").value,

  Compile / unmanagedResourceDirectories := crossDirs("main", "resources").value,
  Test / unmanagedResourceDirectories := crossDirs("test", "resources").value
)

lazy val scalaReflect = (projectMatrix in file("."))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(scala3))
  .settings(commonCrossSettings)
  .jvmPlatform(
    scalaVersions = Seq(scala3),
    settings = Seq(
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )
  .jsPlatform(
    scalaVersions = Seq(scala3),
    settings = commonJsSettings
  )

lazy val scalaReflectJvm = scalaReflect.jvm(scala3)
lazy val scalaReflectJs = scalaReflect.js(scala3)

lazy val root = Project(id = "scala-reflect-root", base = file("."))
  .aggregate(scalaReflectJvm, scalaReflectJs)
  .settings(
    name := "scala-reflect-root",
    moduleName := "scala-reflect-root",
    publish / skip := true,
    Compile / unmanagedSourceDirectories := Seq.empty,
    Test / unmanagedSourceDirectories := Seq.empty,
    Compile / unmanagedResourceDirectories := Seq.empty,
    Test / unmanagedResourceDirectories := Seq.empty
  )