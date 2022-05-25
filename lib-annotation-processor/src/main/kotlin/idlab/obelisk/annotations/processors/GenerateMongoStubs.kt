package idlab.obelisk.annotations.processors

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import idlab.obelisk.annotations.api.GenerateStubsFor
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

const val MONGO_PACKAGE = "idlab.obelisk.plugins.metastore.mongo"
const val CLASS_NAME = "AbstractMongoStorageBase"
val MONGO_CLIENT_REF = ClassName("io.vertx.reactivex.ext.mongo", "MongoClient")

private val MONGO_UTILS_PACKAGE = "idlab.obelisk.utils.mongo"

@AutoService(Processor::class)
class GenerateMongoStubs : AbstractProcessor() {

    override fun init(p0: ProcessingEnvironment) {
        super.init(p0)
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        println("getSupportedAnnotationTypes")
        return mutableSetOf(GenerateStubsFor::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val targetElements = roundEnv.getElementsAnnotatedWith(GenerateStubsFor::class.java)
        println("Mongo Stub generator will process ${targetElements.size} elements...")
        if (targetElements.isNotEmpty()) {
            val typeElements =
                targetElements.mapNotNull {
                    it.annotationMirrors.find {
                        println("Checking if annotation ${it.annotationType} matches GenerateStubsFor")
                        it.annotationType.asElement().asType().asTypeName() == GenerateStubsFor::class.asTypeName()
                    }
                }
                    .flatMap { it.elementValues.filterKeys { el -> el.simpleName.toString() == "oblxTypes" }.values }
                    .flatMap { it.value as List<AnnotationValue> }
                    .map { processingEnv.typeUtils.asElement(it.value as TypeMirror) }

            // Generate the Update version of the model class based on the constructed pairs
            val file = FileSpec.builder("$MONGO_PACKAGE.codegen", CLASS_NAME)
                .addImport(MONGO_UTILS_PACKAGE, "rxCreate")
                .addImport(MONGO_UTILS_PACKAGE, "rxUpdate")
                .addImport(MONGO_UTILS_PACKAGE, "rxFindById")
                .addImport(MONGO_UTILS_PACKAGE, "rxFindPaged")
                .addImport(MONGO_UTILS_PACKAGE, "rxDeleteById")
                .addImport("${MONGO_UTILS_PACKAGE}.query", "fromFilter")
                .addImport(MONGO_UTILS_PACKAGE, "fromSortMap")
                .addImport("idlab.obelisk.utils.service.reactive", "toSingleNullSafe")
                .addType(
                    TypeSpec.classBuilder(CLASS_NAME)
                        .addSuperinterface(ClassName(STORAGE_PACKAGE, STORAGE_BASE_NAME))
                        .addModifiers(KModifier.ABSTRACT)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter("mongoClient", MONGO_CLIENT_REF)
                                .addStatement("this.%N = %N", "mongoClient", "mongoClient")
                                .build()
                        )
                        .addProperty("mongoClient", MONGO_CLIENT_REF, KModifier.PRIVATE)
                        .addFunctions(typeElements.flatMap {
                            listOf(
                                generateCreate(it),
                                generateUpdate(it),
                                generateGet(it),
                                generateQuery(it),
                                generateCount(it),
                                generateRemove(it)
                            )
                        })
                        .build()
                )
                .build()

            val kaptKotlinGeneratedDir =
                processingEnv.options[OblxTypeProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME]
            file.writeTo(File(kaptKotlinGeneratedDir, CLASS_NAME))
        }
        return false
    }

    private fun generateCreate(element: Element): FunSpec {
        val typeName = element.simpleName.toString()
        val typeAttr = typeName.decapitalize()
        return OblxTypeProcessor.createTypeDef(element)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return mongoClient.rxCreate(\"${typeAttr}s\", ${typeAttr}).toSingleNullSafe(${typeAttr}.id)")
            .build()
    }

    private fun generateUpdate(element: Element): FunSpec {
        val typeName = element.simpleName.toString()
        val typeAttr = typeName.decapitalize()
        return OblxTypeProcessor.updateTypeDef(processingEnv, element, false)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return mongoClient.rxUpdate(\"${typeAttr}s\", ${typeAttr}Id, update, nullableFields.map{it.toString()}.toSet())")
            .build()
    }

    private fun generateGet(element: Element): FunSpec {
        val typeName = element.simpleName.toString()
        val typeAttr = typeName.decapitalize()
        return OblxTypeProcessor.getTypeDef(element)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return mongoClient.rxFindById(\"${typeAttr}s\", ${typeAttr}Id, ${typeName}::class.java)")
            .build()
    }

    private fun generateQuery(element: Element): FunSpec {
        val typeName = element.simpleName.toString()
        val typeAttr = typeName.decapitalize()
        return OblxTypeProcessor.queryTypeDef(element, false)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return mongoClient.rxFindPaged(\"${typeAttr}s\", fromFilter(filter), ${typeName}::class.java, limit, cursor, sort?.let{fromSortMap(it)})")
            .build()
    }

    private fun generateCount(element: Element): FunSpec {
        val typeName = element.simpleName.toString()
        val typeAttr = typeName.decapitalize()
        return OblxTypeProcessor.countTypeDef(element)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return mongoClient.rxCount(\"${typeAttr}s\", fromFilter(filter))")
            .build()
    }

    private fun generateRemove(element: Element): FunSpec {
        val typeName = element.simpleName.toString()
        val typeAttr = typeName.decapitalize()
        return OblxTypeProcessor.removeTypeDef(element)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return mongoClient.rxDeleteById(\"${typeAttr}s\", ${typeAttr}Id)")
            .build()
    }

}