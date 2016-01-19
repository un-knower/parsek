package com.github.andr83.parsek.spark

import com.github.andr83.parsek.spark.SparkPipeContext._
import com.github.andr83.parsek.{LongCounter, PipeContext}
import org.apache.spark._
import org.apache.spark.serializer.JavaSerializer

import scala.collection.mutable.{HashMap => MutableHashMap}

/**
 * @author andr83
 */
class SparkPipeContext(acc: Accumulable[MutableHashMap[StringTuple2, Long], (StringTuple2, Long)]) extends PipeContext {

  override def getCounter(groupName: String, name: String): LongCounter = {
    val key = (groupName, name)
    if (!counters.contains(key)) {
      val counter = new LongCounter() {
        override def +=(inc: Long): LongCounter = {
          acc += key -> inc
          super.+=(inc)
        }
      }
      counters += key -> counter
    }
    counters.get(key).get
  }

  override def getCounters: Map[(String, String), Long] = acc.value.toMap
}

object SparkPipeContext {
  type StringTuple2 = (String, String)

  implicit object LongCountersParam extends AccumulableParam[MutableHashMap[StringTuple2, Long], (StringTuple2, Long)] {

    def addAccumulator(acc: MutableHashMap[StringTuple2, Long], elem: (StringTuple2, Long)): MutableHashMap[StringTuple2, Long] = {
      val (k1, v1) = elem
      acc += acc.find(_._1 == k1).map {
        case (k2, v2) => k2 -> (v1 + v2)
      }.getOrElse(elem)
      acc
    }

    /*
     * This method is allowed to modify and return the first value for efficiency.
     *
     * @see org.apache.spark.GrowableAccumulableParam.addInPlace(r1: R, r2: R): R
     */
    def addInPlace(acc1: MutableHashMap[StringTuple2, Long], acc2: MutableHashMap[StringTuple2, Long]): MutableHashMap[StringTuple2, Long] = {
      acc2.foreach(elem => addAccumulator(acc1, elem))
      acc1
    }

    /*
     * @see org.apache.spark.GrowableAccumulableParam.zero(initialValue: R): R
     */
    def zero(initialValue: MutableHashMap[StringTuple2, Long]): MutableHashMap[StringTuple2, Long] = {
      val ser = new JavaSerializer(new SparkConf(false)).newInstance()
      val copy = ser.deserialize[MutableHashMap[StringTuple2, Long]](ser.serialize(initialValue))
      copy.clear()
      copy
    }
  }

  def apply(sc: SparkContext): SparkPipeContext =
    new SparkPipeContext(sc.accumulable(MutableHashMap.empty[StringTuple2, Long])(LongCountersParam))

  def copy(sc: SparkContext, pipeContext: PipeContext): SparkPipeContext = {
    val spc = new SparkPipeContext(sc.accumulable(MutableHashMap.empty[StringTuple2, Long])(LongCountersParam))
    pipeContext.getCounters foreach {
      case ((groupName, name), count) => spc.getCounter(groupName, name) += count
    }
    spc
  }
}