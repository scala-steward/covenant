package covenant.ws

import chameleon._
import covenant.{RequestOperation, TransportException}
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import mycelium.client._
import mycelium.core.EventualResult
import mycelium.core.message._
import sloth._

import scala.concurrent.duration._

sealed class WsRequestTransport[PickleType, ErrorType](
  mycelium: WebsocketClient[PickleType, ErrorType]
)(implicit scheduler: Scheduler) extends RequestTransport[PickleType, RequestOperation[ErrorType, ?]] with Cancelable {

  private val defaultTransport = requestWith()
  def apply(request: Request[PickleType]): RequestOperation[ErrorType, PickleType] = defaultTransport(request)

  def requestWith(sendType: SendType = SendType.WhenConnected, timeout: Option[FiniteDuration] = Some(30 seconds)) = new RequestTransport[PickleType, RequestOperation[ErrorType, ?]] {
    def apply(request: Request[PickleType]): RequestOperation[ErrorType, PickleType] = {
      val responseStream = mycelium.send(request.path, request.payload, sendType, timeout)
      RequestOperation(
        responseStream.flatMap {
          case EventualResult.Single(v) => Task.pure(Right(v))
          case EventualResult.Error(err) => Task.pure(Left(err))
          case EventualResult.Stream(_) => Task.raiseError(TransportException.UnexpectedResult(s"Request (${request.path}) expects single result value, but got stream result"))
        },
        Observable.fromTask(responseStream).flatMap {
          case EventualResult.Stream(o) => o.map(Right.apply)
          case EventualResult.Error(err) => Observable.pure(Left(err))
          case EventualResult.Single(_) => Observable.raiseError(TransportException.UnexpectedResult(s"Request (${request.path}) expects stream result, but got single result value"))
        })
    }
  }

  def connected: Observable[Boolean] = mycelium.connected

  def cancel(): Unit = mycelium.cancel()
}
object WsRequestTransport {
  def fromConnection[PickleType, ErrorType](
    uri: String,
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig = WebsocketClientConfig()
  )(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, ErrorType], PickleType]
  ): WsRequestTransport[PickleType, ErrorType] = {
    val mycelium = WebsocketClient[PickleType, ErrorType](uri, connection, config)
    new WsRequestTransport[PickleType, ErrorType](mycelium)
  }
}
