package covenant.ws

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import akka.stream.StreamRefMessages.Payload
import chameleon._
import covenant.RequestResponse
import covenant.api._
import covenant.ws.api._
import monix.eval.Task
import monix.execution.Scheduler
import mycelium.core._
import mycelium.core.message._
import mycelium.server._
import sloth._

import scala.concurrent.Future

object AkkaWsRoute {
  case class UnhandledServerFailure(failure: ServerFailure) extends Exception(s"Unhandled server failure: $failure")

  def defaultServerConfig = WebsocketServerConfig(bufferSize = 100, overflowStrategy = OverflowStrategy.fail, parallelism = Runtime.getRuntime.availableProcessors)

//  def fromApiRouter[PickleType : AkkaMessageBuilder, ErrorType, Event, State](
//    router: Router[PickleType, RawServerDsl.ApiFunction[Event, State, ?]],
//    api: WsApiConfiguration[Event, ErrorType, State],
//    config: WebsocketServerConfig = defaultServerConfig
//  )(implicit
//    system: ActorSystem,
//    scheduler: Scheduler,
//    serializer: Serializer[ServerMessage[PickleType, ErrorType], PickleType],
//    deserializer: Deserializer[ClientMessage[PickleType], PickleType]) = {
//
//    val handler = new ApiRequestHandler[PickleType, Event, ErrorType, State](api, router)
//    routerToRoute(router, handler, config)
//  }

  def fromRouter[PickleType : AkkaMessageBuilder, ErrorType](
    router: Router[PickleType, RequestResponse[Unit, ErrorType, ?]],
    config: WebsocketServerConfig = defaultServerConfig,
    recoverServerFailure: PartialFunction[ServerFailure, ErrorType] = PartialFunction.empty,
    recoverThrowable: PartialFunction[Throwable, ErrorType] = PartialFunction.empty)(implicit
    system: ActorSystem,
    scheduler: Scheduler,
    serializer: Serializer[ServerMessage[PickleType, ErrorType], PickleType],
    deserializer: Deserializer[ClientMessage[PickleType], PickleType]): Route = fromRouterWithState[PickleType, ErrorType, Unit](router, config, (), _ => true, recoverServerFailure, recoverThrowable)

  def fromRouterWithState[PickleType : AkkaMessageBuilder, ErrorType, State](
    router: Router[PickleType, RequestResponse[State, ErrorType, ?]],
    config: WebsocketServerConfig = defaultServerConfig,
    initialState: State,
    isStateValid: State => Boolean,
    recoverServerFailure: PartialFunction[ServerFailure, ErrorType] = PartialFunction.empty,
    recoverThrowable: PartialFunction[Throwable, ErrorType] = PartialFunction.empty)(implicit
    system: ActorSystem,
    scheduler: Scheduler,
    serializer: Serializer[ServerMessage[PickleType, ErrorType], PickleType],
    deserializer: Deserializer[ClientMessage[PickleType], PickleType]): Route = {

    val handler: RequestHandler[PickleType, ErrorType, State] = ???

    routerToRoute(router, handler, config)
  }

  private def routerToRoute[PickleType : AkkaMessageBuilder, Result[_], ErrorType, Event, State](
    router: Router[PickleType, Result],
    handler: RequestHandler[PickleType, ErrorType, State],
    config: WebsocketServerConfig
  )(implicit
    system: ActorSystem,
    scheduler: Scheduler,
    serializer: Serializer[ServerMessage[PickleType, ErrorType], PickleType],
    deserializer: Deserializer[ClientMessage[PickleType], PickleType]): Route = {

    val websocketServer = WebsocketServer[PickleType, ErrorType, State](config, handler)
    get {
      handleWebSocketMessages(websocketServer.flow())
    }
  }
}
