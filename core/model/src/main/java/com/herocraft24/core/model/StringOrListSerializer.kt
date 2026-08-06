package com.herocraft24.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Custom serializer for fields that can be either a String or List<String> in JSON.
 * This handles the case where some JSON files have "subcategory": "value" (string)
 * and others have "subcategory": ["value"] (array).
 */
object StringOrListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("StringOrList", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: List<String>) {
        when (encoder) {
            is JsonEncoder -> {
                if (value.size == 1) {
                    encoder.encodeJsonElement(JsonPrimitive(value[0]))
                } else {
                    encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
                }
            }
            else -> encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
        }
    }
    
    override fun deserialize(decoder: Decoder): List<String> {
        return when (decoder) {
            is JsonDecoder -> {
                when (val element = decoder.decodeJsonElement()) {
                    is JsonPrimitive -> listOf(element.content)
                    else -> decoder.json.decodeFromJsonElement(ListSerializer(String.serializer()), element)
                }
            }
            else -> decoder.decodeSerializableValue(ListSerializer(String.serializer()))
        }
    }
}
