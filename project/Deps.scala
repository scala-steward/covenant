import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Deps {
  // hack to expand %%% in settings, needs .value in build.sbt
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.4")
  val scalajs = new {
    val dom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.5")
  }
  val sloth = dep("com.github.cornerman.sloth" %%% "sloth" % "934d5f6")
  val mycelium = dep("com.github.cornerman.mycelium" %%% "mycelium" % "be0d69c")
  val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0-RC2")
  val akka = new {
    private val version = "2.5.11"
    val http = dep("com.typesafe.akka" %% "akka-http" % "10.1.0")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
  }
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.0")
  val scribe = dep("com.outr" %%% "scribe" % "2.3.0")
  val monix = dep("io.monix" %%% "monix" % "3.0.0-RC1")
}
