package com.github.andr83.parsek.spark

import akka.actor.{ActorRef, ActorSystem, Props}
import com.github.andr83.parsek._
import com.github.andr83.parsek.spark.pipe.RDDPipe
import com.github.andr83.parsek.spark.sink.Sink
import com.github.andr83.parsek.spark.source.Source
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.collection.mutable

/**
  * @author andr83
  */
object ParsekJob extends SparkJob {

  val DefaultFlow = "default"

  lazy val actorSystem = ActorSystem("ParsekJob")
  private[ParsekJob] val actors = mutable.Map.empty[String, ActorRef]

  def getActor(name: String, props: Props): ActorRef = ParsekJob.synchronized {
    actors.getOrElseUpdate(name, actorSystem.actorOf(props))
  }

  override def job(): Unit = {
    val startTime = System.currentTimeMillis()

    val sourcesByFlow = config.as[List[Config]]("sources")
      .groupBy(_.as[Option[String]]("flow") getOrElse DefaultFlow)
      .mapValues(_.map(Source.apply))

    val repository = new FlowRepository(sc)

    SparkPipeContext.setGlobalContext(sc)
    sourcesByFlow foreach {
      case (flow, sources) =>
        val rdds = sources.map(_ (this))
        val rdd = rdds.tail.foldRight(rdds.head)(_.union(_))
        val pipeContext = repository.getContext(flow)

        pipeContext.getCounter(PipeContext.InfoGroup, PipeContext.InputRowsGroup) += rdd.count()
//        rdd foreachPartition (it=> {
//          pipeContext.getCounter(PipeContext.InfoGroup, PipeContext.InputRowsGroup) += it.size
//        })
        repository += (flow -> rdd)
    }

    val pipeConfigs = config.as[Option[List[Config]]]("pipes") getOrElse List.empty[Config]

    nextPipe(pipeConfigs, repository)

    val sinkConfigs = config.as[List[Config]]("sinks") groupBy (_.as[Option[String]]("flow") getOrElse DefaultFlow)
    val sinkFlows = sinkConfigs.keySet

    repository.rdds filterKeys sinkFlows.contains foreach {
      case (flow, rdd) =>
        SparkPipeContext.setGlobalContext(rdd.sparkContext)
        val sinks = sinkConfigs.get(flow).get map Sink.apply
        val cachedRdd = if (sinks.length > 1) rdd.cache() else rdd
        val pipeContext = repository.getContext(flow)

        pipeContext.getCounter(PipeContext.InfoGroup, PipeContext.OutputRowsGroup) += cachedRdd.count()
//        cachedRdd foreachPartition (it=> {
//          pipeContext.getCounter(PipeContext.InfoGroup, PipeContext.OutputRowsGroup) += it.size
//        })

        sinks.foreach(_.sink(cachedRdd, startTime))

        logger.info(s"Flow $flow counters:")
        logger.info(s"Duration: ${System.currentTimeMillis() - startTime}ms")
        pipeContext.getCounters.toSeq.sortWith(_._1.toString() < _._1.toString()) foreach { case (key, count) =>
          logger.info(s"$key: $count")
        }
    }
  }

  def nextPipe(pipes: List[Config], repository: FlowRepository): Unit = pipes match {
    case head :: tail =>
      runPipe(head, repository)
      nextPipe(tail, repository)
    case Nil =>
  }

  def runPipe(pipeConfig: Config, repository: FlowRepository): Unit = {
    val flow = pipeConfig.as[Option[String]]("flow") getOrElse DefaultFlow
    val pipe = RDDPipe(pipeConfig)

    pipe.run(flow,repository)
  }
}
