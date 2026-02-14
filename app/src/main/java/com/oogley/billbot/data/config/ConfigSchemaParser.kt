package com.oogley.billbot.data.config

import kotlinx.serialization.json.*

sealed class ConfigField {
    abstract val path: String
    abstract val label: String
    abstract val description: String?

    data class Text(
        override val path: String,
        override val label: String,
        override val description: String?,
        val value: String,
        val placeholder: String? = null
    ) : ConfigField()

    data class Number(
        override val path: String,
        override val label: String,
        override val description: String?,
        val value: Double?,
        val min: Double? = null,
        val max: Double? = null
    ) : ConfigField()

    data class Toggle(
        override val path: String,
        override val label: String,
        override val description: String?,
        val value: Boolean
    ) : ConfigField()

    data class Select(
        override val path: String,
        override val label: String,
        override val description: String?,
        val value: String,
        val options: List<String>
    ) : ConfigField()

    data class Section(
        override val path: String,
        override val label: String,
        override val description: String?,
        val children: List<ConfigField>
    ) : ConfigField()

    data class ReadOnly(
        override val path: String,
        override val label: String,
        override val description: String?,
        val value: String
    ) : ConfigField()
}

object ConfigSchemaParser {

    fun parse(schema: JsonElement, config: JsonElement?): List<ConfigField> {
        val schemaObj = schema.jsonObject
        val properties = schemaObj["properties"]?.jsonObject ?: return emptyList()
        val configObj = config?.jsonObject

        return parseProperties(properties, configObj, "", schemaObj)
    }

    private fun parseProperties(
        properties: JsonObject,
        configObj: JsonObject?,
        pathPrefix: String,
        parentSchema: JsonObject
    ): List<ConfigField> {
        val order = parentSchema["x-ui-order"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }

        val keys = if (order != null) {
            order.filter { it in properties } + properties.keys.filter { it !in order }
        } else {
            properties.keys.toList()
        }

        return keys.mapNotNull { key ->
            val propSchema = properties[key]?.jsonObject ?: return@mapNotNull null
            val fullPath = if (pathPrefix.isEmpty()) key else "$pathPrefix.$key"
            val configValue = configObj?.get(key)

            // Skip hidden fields
            if (propSchema["x-ui-hidden"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null

            val label = propSchema["title"]?.jsonPrimitive?.contentOrNull
                ?: key.replace(Regex("([A-Z])"), " $1").trim().replaceFirstChar { it.uppercase() }
            val description = propSchema["description"]?.jsonPrimitive?.contentOrNull
            val isReadOnly = propSchema["x-ui-readonly"]?.jsonPrimitive?.booleanOrNull == true
                    || propSchema["readOnly"]?.jsonPrimitive?.booleanOrNull == true

            val type = propSchema["type"]?.jsonPrimitive?.contentOrNull

            when {
                isReadOnly -> ConfigField.ReadOnly(
                    path = fullPath,
                    label = label,
                    description = description,
                    value = configValue?.toString()?.removeSurrounding("\"") ?: ""
                )
                type == "object" && propSchema["properties"] != null -> {
                    val childProps = propSchema["properties"]!!.jsonObject
                    val childConfig = configValue?.jsonObject
                    ConfigField.Section(
                        path = fullPath,
                        label = label,
                        description = description,
                        children = parseProperties(childProps, childConfig, fullPath, propSchema)
                    )
                }
                type == "boolean" -> ConfigField.Toggle(
                    path = fullPath,
                    label = label,
                    description = description,
                    value = configValue?.jsonPrimitive?.booleanOrNull ?: false
                )
                type == "number" || type == "integer" -> ConfigField.Number(
                    path = fullPath,
                    label = label,
                    description = description,
                    value = configValue?.jsonPrimitive?.doubleOrNull,
                    min = propSchema["minimum"]?.jsonPrimitive?.doubleOrNull,
                    max = propSchema["maximum"]?.jsonPrimitive?.doubleOrNull
                )
                propSchema["enum"] != null -> {
                    val options = propSchema["enum"]!!.jsonArray.mapNotNull {
                        it.jsonPrimitive.contentOrNull
                    }
                    ConfigField.Select(
                        path = fullPath,
                        label = label,
                        description = description,
                        value = configValue?.jsonPrimitive?.contentOrNull ?: options.firstOrNull() ?: "",
                        options = options
                    )
                }
                else -> ConfigField.Text(
                    path = fullPath,
                    label = label,
                    description = description,
                    value = configValue?.toString()?.removeSurrounding("\"") ?: "",
                    placeholder = propSchema["default"]?.jsonPrimitive?.contentOrNull
                )
            }
        }
    }
}
