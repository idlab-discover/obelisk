package idlab.obelisk.annotations.processors

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import idlab.obelisk.annotations.api.OblxType
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import net.pearx.kasechange.toScreamingSnakeCase
import org.jetbrains.annotations.Nullable
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

const val OBELISK_DEF_PACKAGE = "idlab.obelisk.definitions"
const val STORAGE_PACKAGE = "idlab.obelisk.storage.codegen"
const val STORAGE_BASE_NAME = "StorageBase"

@AutoService(Processor::class)
class OblxTypeProcessor : AbstractProcessor() {

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

        internal fun createTypeDef(element: Element): FunSpec.Builder {
            val name = element.simpleName.toString()
            return FunSpec.builder("create$name")
                .addParameter(name.decapitalize(), element.asType().asTypeName())
                .returns(
                    Single::class.asClassName().parameterizedBy(String::class.asClassName())
                )
        }

        internal fun updateTypeDef(
            processingEnv: ProcessingEnvironment,
            element: Element,
            specifyDefaults: Boolean = true
        ): FunSpec.Builder {
            val name = element.simpleName.toString()

            val nullFieldsParam = ParameterSpec.builder(
                "nullableFields",
                Set::class.asTypeName().parameterizedBy(
                    ClassName(
                        "${processingEnv.elementUtils.getPackageOf(element)}.codegen",
                        "${element.simpleName}NullableField"
                    )
                )
            )
            if (specifyDefaults) {
                nullFieldsParam.defaultValue("setOf()")
            }

            return FunSpec.builder("update$name")
                .addParameter("${name.decapitalize()}Id", String::class)
                .addParameter(
                    "update",
                    ClassName(
                        "${processingEnv.elementUtils.getPackageOf(element)}.codegen",
                        "${element.simpleName}Update"
                    )
                )
                .addParameter(nullFieldsParam.build())
                .returns(Completable::class)
        }

        internal fun queryTypeDef(element: Element, specifyDefaults: Boolean = true): FunSpec.Builder {
            val name = element.simpleName.toString()
            val limitParam = ParameterSpec.builder("limit", Int::class)
            val cursorParam = ParameterSpec.builder("cursor", String::class.asTypeName().copy(nullable = true))
            val sortParam = ParameterSpec.builder(
                "sort", Map::class.asTypeName().parameterizedBy(
                    Enum::class.asTypeName().parameterizedBy(WildcardTypeName.producerOf(ANY.copy(nullable = true))),
                    ClassName(
                        OBELISK_DEF_PACKAGE, "Ordering"
                    )
                ).copy(nullable = true)
            )
            if (specifyDefaults) {
                limitParam.defaultValue("%L", element.getAnnotation(OblxType::class.java).defaultPageSize)
                cursorParam.defaultValue("%L", null).build()
                sortParam.defaultValue("%L", null).build()
            }
            return FunSpec.builder("query${name}s")
                .addParameter("filter", ClassName(OBELISK_DEF_PACKAGE, "FilterExpression"))
                .addParameter(limitParam.build())
                .addParameter(cursorParam.build())
                .addParameter(sortParam.build())
                .returns(
                    Single::class.asTypeName().parameterizedBy(
                        ClassName(
                            OBELISK_DEF_PACKAGE,
                            "PagedResult"
                        ).parameterizedBy(element.asType().asTypeName())
                    )
                )
        }

        internal fun getTypeDef(element: Element): FunSpec.Builder {
            val name = element.simpleName.toString()
            return FunSpec.builder("get$name")
                .addParameter("${name.decapitalize()}Id", String::class)
                .returns(Maybe::class.asTypeName().parameterizedBy(element.asType().asTypeName()))
        }

        internal fun countTypeDef(element: Element): FunSpec.Builder {
            val name = element.simpleName.toString()
            return FunSpec.builder("count${name}s")
                .addParameter("filter", ClassName(OBELISK_DEF_PACKAGE, "FilterExpression"))
                .returns(Single::class.asTypeName().parameterizedBy(Long::class.asTypeName()))
        }

        internal fun removeTypeDef(element: Element): FunSpec.Builder {
            val name = element.simpleName.toString()
            return FunSpec.builder("remove$name")
                .addParameter("${name.decapitalize()}Id", String::class)
                .returns(Completable::class)
        }
    }

    override fun init(p0: ProcessingEnvironment) {
        super.init(p0)
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        println("getSupportedAnnotationTypes")
        return mutableSetOf(OblxType::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        println("process")
        val targetElements = roundEnv.getElementsAnnotatedWith(OblxType::class.java)
        if (targetElements.isNotEmpty()) {
            targetElements
                .forEach {
                    println("Processing: ${it.simpleName}")
                    val pack = processingEnv.elementUtils.getPackageOf(it).toString()
                    generateClass(it, pack)
                }
            generateStorageInterface(targetElements.toList())
        }
        return false
    }

    private fun generateStorageInterface(elements: List<Element>) {
        // Generate the basic Storage Interface (to be extended in the Catalog) based on the discovered Oblx Types.
        val file = FileSpec.builder(STORAGE_PACKAGE, STORAGE_BASE_NAME)
            .addType(
                TypeSpec.interfaceBuilder(STORAGE_BASE_NAME)
                    .addFunctions(elements.filter { it.getAnnotation(OblxType::class.java).rootType }
                        .sortedBy { it.simpleName.toString() }.flatMap {
                            val name = it.simpleName.toString()
                            println("Generating storage functions for $name")
                            listOf(
                                createTypeDef(it),
                                updateTypeDef(processingEnv, it),
                                getTypeDef(it),
                                queryTypeDef(it),
                                countTypeDef(it),
                                removeTypeDef(it)
                            )
                        }.map { it.addModifiers(KModifier.ABSTRACT).build() }).build()
            )
            .build()

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        file.writeTo(File(kaptKotlinGeneratedDir, STORAGE_BASE_NAME))
    }

    // Type references don't seem to work here, so I've written out the type names manually (regretfully).
    private fun toKotlinType(typeName: TypeName): TypeName {
        return when (typeName) {
            ClassName("java.lang", "Object") -> ClassName("kotlin", "Any")
            ClassName("java.lang", "String") -> ClassName("kotlin", "String")
            ClassName("java.lang", "Integer") -> ClassName("kotlin", "Int")
            ClassName("java.lang", "Long") -> ClassName("kotlin", "Long")
            ClassName("java.lang", "Double") -> ClassName("kotlin", "Double")
            is ParameterizedTypeName -> {
                val convertedTypeParams = typeName.typeArguments.map { t -> toKotlinType(t) }
                when (typeName.rawType) {
                    ClassName("java.util", "List") -> ClassName("kotlin.collections", "List").parameterizedBy(
                        convertedTypeParams
                    )
                    ClassName("java.util", "Set") -> ClassName("kotlin.collections", "Set").parameterizedBy(
                        convertedTypeParams
                    )
                    ClassName("java.util", "Map") -> ClassName("kotlin.collections", "Map").parameterizedBy(
                        convertedTypeParams
                    )
                    else -> typeName
                }
            }
            else -> typeName
        }
    }

    private fun generateClass(klass: Element, pack: String) {
        val name = "${klass.simpleName}Update"
        val generatedClass = ClassName("$pack.codegen", name)
        val fieldElements = klass.enclosedElements.filter { it.kind == ElementKind.FIELD }
        val annotation = klass.getAnnotation(OblxType::class.java)

        val fields = fieldElements
            // Ignore id (this is not an updatable field)
            .filterNot { it.simpleName.toString() == "id" }
            // Map the element to a constructor parameter / property pair
            .map { f ->
                val fieldType = toKotlinType(f.asType().asTypeName()).copy(nullable = true)
                ParameterSpec.builder(f.simpleName.toString(), fieldType)
                    .defaultValue("%L", null).build() to PropertySpec.builder(
                    f.simpleName.toString(),
                    fieldType
                ).initializer(f.simpleName.toString()).build()
            }

        // Arguments for the conversion function (from Type to TypeUpdate)
        val argsCode = fields.map { CodeBlock.of("%L", "instance.${it.first.name}") }.joinToCode(", ", "(", ")")

        // Generate companion object as a holder for the conversion function
        val companion = TypeSpec.companionObjectBuilder()
            .addFunction(
                FunSpec.builder("from")
                    .addParameter(ParameterSpec("instance", klass.asType().asTypeName()))
                    .addCode(CodeBlock.builder().add("return %T", generatedClass).add(argsCode).build())
                    .returns(generatedClass)
                    .build()
            ).build()

        // Generate field overview enum
        val fieldEnum = TypeSpec.enumBuilder("${klass.simpleName}Field")
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("fieldName", String::class).build())
            .addProperty(
                PropertySpec.builder("fieldName", String::class, KModifier.PRIVATE).initializer("fieldName").build()
            )
            .addFunction(
                FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).addStatement("return fieldName")
                    .returns(String::class).build()
            )
        fieldElements.forEach {
            fieldEnum.addEnumConstant(
                it.simpleName.toString().toScreamingSnakeCase(),
                TypeSpec.anonymousClassBuilder().addSuperclassConstructorParameter("%S", it.simpleName.toString())
                    .build()
            )
        }

        // Generate nullable field overview enum
        val nullableFieldEnum = TypeSpec.enumBuilder("${klass.simpleName}NullableField")
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("fieldName", String::class).build())
            .addProperty(
                PropertySpec.builder("fieldName", String::class, KModifier.PRIVATE).initializer("fieldName").build()
            )
            .addFunction(
                FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).addStatement("return fieldName")
                    .returns(String::class).build()
            )
        fieldElements.filterNot { it.simpleName.toString() == "id" }
            .filter { it.getAnnotation(Nullable::class.java) != null }
            .forEach {
                nullableFieldEnum.addEnumConstant(
                    it.simpleName.toString().toScreamingSnakeCase(),
                    TypeSpec.anonymousClassBuilder().addSuperclassConstructorParameter("%S", it.simpleName.toString())
                        .build()
                )
            }

        // Generate the Update version of the model class based on the constructed pairs
        val file = FileSpec.builder("$pack.codegen", klass.simpleName.toString())
        if (annotation.rootType) {
            file.addType(
                TypeSpec.classBuilder(name)
                    .addModifiers(KModifier.DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(fields.map { it.first })
                            .build()
                    )
                    .addProperties(fields.map { it.second })
                    .addType(companion)
                    .build()
            )
        }
        file.addType(fieldEnum.build()).addType(nullableFieldEnum.build())

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        file.build().writeTo(File(kaptKotlinGeneratedDir, name))
    }
}