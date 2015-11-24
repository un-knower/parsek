package com.github.andr83.parsek.pipe

import com.github.andr83.parsek._
import com.typesafe.config.Config
import org.apache.commons.codec.binary.Base64

/**
 * @author andr83
 */
case class Base64Decoder(config: Config) extends TransformPipe(config) {
  override def transformString(str: String)(implicit context: Context): Option[PValue] = Some(PString(
    Base64.decodeBase64(str).asStr
  ))
}