import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Deps {
  // hack to expand %%% in settings, needs .value in build.sbt
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.5")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.6")
  }
  val chameleon = dep("com.github.cornerman.chameleon" %%% "chameleon" % "3e1931d")
  val sloth = dep("com.github.cornerman.sloth" %%% "sloth" % "8d641d9")
  val mycelium = dep("com.github.cornerman.mycelium" %%% "mycelium" % "8bcf980")
  val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0")
  val akka = new {
    private val version = "2.5.13"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.1.2")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
  }
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.0")
  val scribe = dep("com.outr" %%% "scribe" % "2.5.0")
  val monix = dep("io.monix" %%% "monix" % "3.0.0-RC1")
}
