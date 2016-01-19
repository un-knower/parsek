package com.github.andr83.parsek.spark.streaming.pipe

import com.github.andr83.parsek.spark.streaming.StreamFlowRepository
import com.github.andr83.parsek.spark.util.RuntimeUtils
import com.github.andr83.parsek.{PValuePredicate, PipeContext}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

import scala.util.{Failure, Success, Try}

/**
  * Split stream with 2 flows based on predicate
  *
  * @param predicateFactory factory function to return PValue => Boolean instance
  * @param toFlows Flows name to split
  *
  * @author andr83
  */
case class SpanDStreamPipe(predicateFactory: () => PValuePredicate, toFlows: Seq[String]) extends DStreamPipe {

  def this(config: Config) = this(
    predicateFactory = () => RuntimeUtils.compileFilterFn(config.as[String]("predicate")),
    toFlows = config.as[Seq[String]]("toFlows")
  )

  assert(toFlows.size == 2)

  lazy val predicate: PValuePredicate = predicateFactory()

  override def run(flow: String, repository: StreamFlowRepository): Unit = {
    val cachedStream = repository.getStream(flow).cache()

    val firstContext = repository.getContext(toFlows.head, flow)
    val secondContext = repository.getContext(toFlows.last, flow)

    repository += (toFlows.head -> cachedStream.filter(r => Try(predicate(r)) match {
      case Success(res) => res
      case Failure(error) =>
        logger.error(error.toString)
        firstContext.getCounter(PipeContext.ErrorGroup, error.getClass.getSimpleName) += 1
        false
    }))

    repository += (toFlows.last -> cachedStream.filter(r =>Try(predicate(r)) match {
      case Success(res) => !res
      case Failure(error) =>
        logger.error(error.toString)
        secondContext.getCounter(PipeContext.ErrorGroup, error.getClass.getSimpleName) += 1
        false
    }))
  }
}
