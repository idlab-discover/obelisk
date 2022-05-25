package idlab.obelisk.services.pub.ngsi.impl.model

import com.fasterxml.jackson.annotation.JsonInclude
import idlab.obelisk.services.pub.ngsi.Constants

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class EntityTypeList(
    val id: String,
    val type: String = "EntityTypeList",
    val typeList: List<String>
) {
    constructor(entityTypes: List<EntityType>) : this(
        id = "${Constants.OBLX_TYPES_LIST_URN_BASE}${entityTypes.hashCode()}",
        typeList = entityTypes.map { it.id })
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class EntityType(
    val id: String,
    val type: String = "EntityType",
    // typeName is filled in when 'details=true' for types request
    val typeName: String? = null,
    // attributeNames is filled in when 'details=true' for types request
    val attributeNames: List<String>? = null
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class EntityTypeInfo(
    val id: String,
    val type: String = "EntityTypeInformation",
    val typeName: String,
    val entityCount: Long,
    val attributeDetails: List<Attribute>
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class AttributeList(
    val id: String,
    val type: String = "AttributeList",
    val attributeList: List<String>
) {
    constructor(entityTypes: List<Attribute>) : this(
        id = "${Constants.OBLX_ATTRIBUTE_LIST_URN_BASE}${entityTypes.hashCode()}",
        attributeList = entityTypes.map { it.id })
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Attribute(
    val id: String,
    val type: String = "Attribute",
    val attributeName: String,
    val attributeCount: Long? = null,
    val attributeTypes: List<String>? = null,
    val typeNames: List<String>? = null
)


