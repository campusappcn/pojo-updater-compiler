package cn.campusapp.updatercompiler.manager;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

import cn.campusapp.updater.Updater;
import cn.campusapp.updater.UpdaterManager;

/**
 * Created by chen on 16/1/7.
 */
public class ManagerGenerator {
    private static final String sMANAGER_NAME = "UpdaterManagerImpl";
    private final PackageElement mPackageElement;
    private final HashMap<TypeMirror, TypeElement> mEntityUpdaterMap = new HashMap<>();

    public ManagerGenerator(PackageElement packageElement) {
        mPackageElement = packageElement;
    }

    public void put(TypeMirror entityTypeMirror, TypeElement updaterType) {
        mEntityUpdaterMap.put(entityTypeMirror, updaterType);
    }

    private FieldSpec generateMapField() {
        ParameterizedTypeName type = ParameterizedTypeName.get(ClassName.get(HashMap.class),
                ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("?")),
                ParameterizedTypeName.get(ClassName.get(Updater.class), TypeVariableName.get("?"))
        );
        return FieldSpec.builder(type, "mUpdaterMap", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", HashMap.class)
                .build();
    }

    private MethodSpec generateConstructor(FieldSpec mapperField) {
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        for (final Map.Entry<TypeMirror, TypeElement> classTypeElementEntry : mEntityUpdaterMap.entrySet()) {
            final TypeMirror entityClass = classTypeElementEntry.getKey();
            final TypeElement typeElement = classTypeElementEntry.getValue();
            builder.addStatement("$N.put($T.class, new $T())", mapperField, entityClass, typeElement.asType());
        }
        return builder.build();
    }

    private MethodSpec generateGetUpdaterMethod(FieldSpec mapField) {
        TypeVariableName typeVariableName = TypeVariableName.get("T");
        ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Updater.class), typeVariableName);
        ParameterSpec parameterSpec = ParameterSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Class.class), typeVariableName),
                "tClass"
        ).build();

        final String returnVariableName = "updater";
        return MethodSpec.methodBuilder("getUpdater")
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(typeVariableName)
                .addParameter(parameterSpec)
                .returns(returnType)
                .addStatement("$T $N = ($T)$N.get($N)", returnType, returnVariableName, returnType, mapField, parameterSpec)
                .beginControlFlow("if ($N == null)", returnVariableName)
                .addStatement("throw new $T(\"Updater for $N not found\")", RuntimeException.class, parameterSpec)
                .endControlFlow()
                .addStatement("return updater")
                .build();
    }

    private TypeSpec generateTypeSpec() {
        FieldSpec mapField = generateMapField();
        return TypeSpec.classBuilder(sMANAGER_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(UpdaterManager.class)
                .addField(mapField)
                .addMethod(generateConstructor(mapField))
                .addMethod(generateGetUpdaterMethod(mapField))
                .build();
    }

    private JavaFile generateJavaFile() {
        return JavaFile.builder(mPackageElement.getQualifiedName().toString(),
                generateTypeSpec())
                .build();
    }

    public void writeToSource(ProcessingEnvironment processingEnvironment) throws IOException {
        JavaFile javaFile = generateJavaFile();
        JavaFileObject jfo = processingEnvironment.getFiler().createSourceFile(sMANAGER_NAME, mPackageElement);
        try (Writer writer = jfo.openWriter()) {
            javaFile.writeTo(writer);
        }
    }
}
