package covenant.ws.api

import covenant.RequestResponse
import covenant.api._
import covenant.util.StopWatch
import covenant.ws.AkkaWsRoute.UnhandledServerFailure
import monix.eval.Task
import monix.execution.Scheduler
import mycelium.core.EventualResult
import mycelium.server._
import sloth._

import scala.concurrent.Future

class ApiRequestHandler[PickleType, Event, ErrorType, State](
  router: Router[PickleType, RequestResponse[State, ErrorType, ?]],
  config: WebsocketServerConfig,
  initialState: State,
  isStateValid: State => Boolean,
  recoverServerFailure: PartialFunction[ServerFailure, ErrorType],
  recoverThrowable: PartialFunction[Throwable, ErrorType]
)(implicit scheduler: Scheduler) extends StatefulRequestHandler[PickleType, ErrorType, State] {
  import covenant.util.LogHelper._

  def initialState = Future.successful(initialState)

  override def onClientConnect(client: ClientId, state: Future[State]): Unit = {
    scribe.info(s"$client started")
  }

  override def onClientDisconnect(client: ClientId, state: Future[State], reason: DisconnectReason): Unit = {
    scribe.info(s"$client stopped: $reason")
  }

  override def onRequest(client: ClientId, originalState: Future[State], path: List[String], payload: PickleType): Response = {
    val watch = StopWatch.started

    val state = validateState(originalState)
    val request = Request(path, payload)
    router(request) match {

      case RouterResult.Success(arguments, result) =>
        val response = result match {
          case result: RequestResponse.Result[State, ErrorType, PickleType] =>
            Task.pure(result)
          case stateFun: RequestResponse.StateFunction[State, ErrorType, PickleType] => Task.fromFuture(state).map { state =>
            stateFun.function(state)
          }
        }

        val resultTask: Task[EventualResult[PickleType, ErrorType]] = response.flatMap(_.value match {
          case RequestResponse.Single(task) => task.map {
            case Right(v) =>
              scribe.info(s"http -->[response] ${requestLogLine(path, arguments, v)}. Took ${watch.readHuman}.")
              EventualResult.Single(v)
            case Left(e) =>
              scribe.warn(s"http -->[error] ${requestLogLine(path, arguments, e)}. Took ${watch.readHuman}.")
              EventualResult.Error(e)
          }
          case RequestResponse.Stream(task) => task.map {
            case Right(observable) =>
              scribe.info(s"http -->[stream:started] ${requestLogLine(path, arguments)}. Took ${watch.readHuman}.")
              val events = observable.map { v =>
                scribe.info(s"http -->[stream] ${requestLogLine(path, arguments, v)}. Took ${watch.readHuman}.")
                v
              }.doOnComplete { () =>
                scribe.info(s"http -->[stream:complete] ${requestLogLine(path, arguments)}. Took ${watch.readHuman}.")
              }.doOnError { t =>
                scribe.warn(s"http -->[stream:error] ${requestLogLine(path, arguments)}. Took ${watch.readHuman}.", t)
              }

              EventualResult.Stream(events)
            case Left(e) =>
              scribe.warn(s"http -->[error] ${requestLogLine(path, arguments, e)}. Took ${watch.readHuman}.")
              EventualResult.Error(e)
          }
        })

        Response(state, resultTask)
      case RouterResult.Failure(arguments, e) =>
        val error = recoverServerFailure.lift(e)
          .fold[Task[EventualResult[PickleType, ErrorType]]](Task.raiseError(UnhandledServerFailure(e)))(e => Task.pure(EventualResult.Error(e)))

        scribe.warn(s"http -->[failure] ${requestLogLine(path, arguments, e)}. Took ${watch.readHuman}.")
        Response(state, error)
//        case f: RawServerDsl.ApiFunction.Single[Event, State, RouterResult.Value[PickleType]] =>
//          val result = f.run(state)
//          val newState = result.state
//
//          ???
//
//        case f: RawServerDsl.ApiFunction.Stream[Event, State, RouterResult.Value[PickleType]] =>
//          val result = f.run(state)
//          val newState = result.state
//
//          ???

//        val returnValue = result.action.value match {
//          case ApiValue.Single(future) => future.map { value =>
//            val rawResult = value.result.map(_.raw)
//            val serializedResult = value.result.map(_.serialized)
//            val events = filterAndDistributeEvents(client)(value.events)
//            scribe.info(s"$client -->[response] ${requestLogLine(path, arguments, rawResult)} / $events. Took ${watch.readHuman}.")
//            client.notify(events)
//            serializedResult
//          }
//          case ApiValue.Stream(observable) =>
//        }
//
//        result.action.events.foreach { rawEvents =>
//          val events = filterAndDistributeEvents(client)(rawEvents)
//          if (events.nonEmpty) {
//            scribe.info(s"$client -->[async] ${requestLogLine(path, arguments, events)}. Took ${watch.readHuman}.")
//            client.notify(events)
//          }
//        }
//
//        Response(newState, returnValue)

//      case RouterResult.Failure(arguments, slothError) =>
//        val error = api.serverFailure(slothError)
//        scribe.warn(s"$client -->[failure] ${requestLogLine(path, arguments, error)}. Took ${watch.readHuman}.")
//        Response(state, Task(EventualResult.Error(error)))

    }
  }

  private def validateState(state: Future[State]): Future[State] = state.flatMap { state =>
    if (isStateValid(state)) Future.successful(state)
    else Future.failed(new Exception("State is invalid"))
  }

//  private def filterAndDistributeEvents[T](client: ClientId)(rawEvents: Seq[Event]): List[Event] = {
//    val scoped = api.scopeOutgoingEvents(rawEvents.toList)
//    api.eventDistributor.publish(scoped.publicEvents, origin = Some(client))
//    scoped.privateEvents
//  }
}
