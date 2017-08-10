package io.protoless.core.error

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.WireFormat.FieldType

import io.protoless.core.fields.FieldTag

/**
  * An exception representing a decoding failure associated with a possible cause
  */
sealed class DecodingFailure(message: String, cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)

case class MissingField(index: Int, fieldType: FieldType, tag: FieldTag) extends DecodingFailure(
  "Field not present in protobuff message.\n" +
  s"Expected to read field at index $index with type ${fieldType.getJavaType.name()}, " +
  s"but next index is ${tag.fieldNumber} with wire type ${DecodingFailure.wireTypeDetail(tag.wireType)} " +
  s"[raw tag: ${tag.tag}]")

case class WrongFieldType(expectedType: FieldType, tag: FieldTag) extends DecodingFailure(
  s"Field read at index ${tag.fieldNumber} doesn't meet field type requirements. " +
  s"Expected type ${expectedType.getJavaType.name()} (wire: ${expectedType.getWireType}) " +
  s"but wire type read is ${DecodingFailure.wireTypeDetail(tag.wireType)} " +
  s"[raw tag: ${tag.tag}]"
)

case class InternalProtobufError(message: String, cause: Throwable) extends DecodingFailure(message, Some(cause))

object DecodingFailure {

  /**
    * Transform a generic Exception into a [[DecodingFailure]] associated with the `field number` of the failure`.
    */
  def fromThrowable(ex: Throwable, index: Int): InternalProtobufError = ex match {
    case err: InvalidProtocolBufferException =>
      val unfinishedMessage = Option(err.getUnfinishedMessage).map(msg => s"\nMessage read: ${msg.toByteArray}").getOrElse("")
      InternalProtobufError(s"Cannot read field at index $index\n${err.getMessage}\n$unfinishedMessage", ex)

    case _ => InternalProtobufError(ex.getMessage, ex)
  }

  /**
    * Build a [[DecodingFailure]] from a message
    */
  def apply(message: String): DecodingFailure = new DecodingFailure(message)

  private val mappingWireTypeWithCompatibleType = Seq(
    (0, "Varint", "int32, int64, uint32, uint64, sint32, sint64, bool, enum"),
    (1, "64-bit", "fixed64, sfixed64, double"),
    (2, "Length-delimited", "string, bytes, embedded messages, packed repeated fields"),
    (3, "Start group", "groups (deprecated)"),
    (4, "End group", "groups (deprecated)"),
    (5, "32-bit", "fixed32, sfixed32, float")
  )

  private[core] def wireTypeDetail(wireType: Int): String = {
    mappingWireTypeWithCompatibleType
      .find(_._1 == wireType)
      .map { case (index, name, compatible) => s"$index:$name ($compatible)" }
      .getOrElse(s"$wireType:Unknown")
  }

}
