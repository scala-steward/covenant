package test

import org.scalatest._

import covenant.ws._
import sloth._
import chameleon.ext.boopickle._
import boopickle.Default._
import java.nio.ByteBuffer
import mycelium.client._
import mycelium.server._

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.actor.ActorSystem

import cats.implicits._

import scala.concurrent.Future

class WsSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {
  trait Api[Result[_]] {
    def fun(a: Int): Result[Int]
    @PathName("funWithDefault")
    def fun(a: Int, b: Int): Result[Int] = fun(a + b)
  }

  object FutureApiImpl extends Api[Future] {
    def fun(a: Int): Future[Int] = Future.successful(a)
  }

  implicit val system = ActorSystem("mycelium")
  implicit val materializer = ActorMaterializer()

  val port = 9999

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

 "simple run" in {
    object Backend {
      val router = Router[ByteBuffer, Future]
        .route[Api[Future]](FutureApiImpl)

      def run() = {
        val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
        val route = router.asWsRoute[ApiError](config, failedRequestError = err => ApiError(err.toString))
        Http().bindAndHandle(route, interface = "0.0.0.0", port = port)
      }
    }

    object Frontend {
      val config = WebsocketClientConfig()
      val client = WsClient[ByteBuffer, Unit, ApiError](s"ws://localhost:$port/ws", config)
      val api = client.sendWithDefault.wire[Api[Future]]
    }

    Backend.run()

    for {
      fun <- Frontend.api.fun(1)
      fun2 <- Frontend.api.fun(1, 2)
    } yield {
      fun mustEqual 1
      fun2 mustEqual 3
    }
  }

  //TODO generalize over this structure, can implement requesthander? --> apidsl
  type Event = String
  type State = String

  case class ApiValue[T](result: T, events: List[Event])
  case class ApiResult[T](state: Future[State], value: Future[ApiValue[T]])
  type ApiResultFun[T] = Future[State] => ApiResult[T]

  case class ApiError(msg: String)

  implicit val apiValueFunctor = cats.derive.functor[ApiValue]
  implicit val apiResultFunctor = cats.derive.functor[ApiResult]
  implicit val apiResultFunFunctor = cats.derive.functor[ApiResultFun]
  //


  "run" in {
    import covenant.ws.api._
    import monix.execution.Scheduler.Implicits.global

    val api = new ApiConfigurationWithDefaults[Event, ApiError, State] {
      override def initialState: State = ""
      override def isStateValid(state: State): Boolean = true
      override def applyEventsToState(state: State, events: Seq[Event]): State = state + " " + events.mkString(",")
      override def serverFailure(error: ServerFailure): ApiError = ApiError(error.toString)
      override def unhandledException(t: Throwable): ApiError = ApiError(t.getMessage)
    }

    object ApiImpl extends Api[api.dsl.ApiFunction] {
      import api.dsl._

      def fun(a: Int): ApiFunction[Int] = Action { state =>
        Future.successful(a)
      }
    }

    object Backend {
      val router = Router[ByteBuffer, api.dsl.ApiFunction]
        .route[Api[api.dsl.ApiFunction]](ApiImpl)

      def run() = {
        val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
        val route = api.asWsRoute(router, config)
        Http().bindAndHandle(route, interface = "0.0.0.0", port = port)
      }
    }

    object Frontend {
      val config = WebsocketClientConfig()
      val client = WsClient[ByteBuffer, Unit, ApiError](s"ws://localhost:$port/ws", config)
      val api = client.sendWithDefault.wire[Api[Future]]
    }

    Backend.run()

    for {
      fun <- Frontend.api.fun(1)
      fun2 <- Frontend.api.fun(1, 2)
    } yield {
      fun mustEqual 1
      fun2 mustEqual 3
    }
  }
}
