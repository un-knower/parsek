package com.github.andr83.parsek.spark

import com.github.andr83.parsek.PValue
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/**
  * Repository to get access to streams and their contexts in flow
  *
  * @param sc SparkContext to create SparkPipeContext instances
  *
  * @author andr83
  */
class  FlowRepository(sc: SparkContext) {
  protected var rddByFlow: Map[String, RDD[PValue]] = Map.empty[String, RDD[PValue]]
  protected var contextByFlow: Map[String, SparkPipeContext] = Map.empty[String, SparkPipeContext]

  def rdds = rddByFlow

  /**
    * Return PipeContext for current flow. If context is not available it will created
    *
    * @param flow flow name
    * @return
    */
  def getContext(flow: String): SparkPipeContext = getContext(flow, flow)

  /**
    * Return PipeContext for current flow. If context is not available it will copied from currentFlow or created
    *
    * @param flow flow name for which return PipeContext
    * @param currentFlow flow which use to create PipeContext if it is not exist
    *
    * @return
    */
  def getContext(flow: String, currentFlow: String): SparkPipeContext = contextByFlow.getOrElse(flow, {
    val context = SparkPipeContext(flow)
    contextByFlow = contextByFlow + (flow -> context)

    if (currentFlow != flow) {
      contextByFlow.get(currentFlow) foreach (currentContext=> {
        SparkPipeContext.copy(currentContext, context)
      })
    }

    context
  })

  /**
    * Return RDD for flow. If stream is not available exeption will thrown
    *
    * @param flow flow name
    * @return
    */
  def getRdd(flow: String): RDD[PValue] = rddByFlow.getOrElse(flow,
    throw new IllegalStateException(s"Flow $flow is unavailable. Please check configuration."))

  /**
    * Assign RDD to flow
    *
    * @param flowRdd
    */
  def +=(flowRdd: (String, RDD[PValue])) = rddByFlow = rddByFlow + flowRdd
}