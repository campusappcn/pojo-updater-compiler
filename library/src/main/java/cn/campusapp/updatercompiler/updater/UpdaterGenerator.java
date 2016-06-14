package cn.campusapp.updatercompiler.updater;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

import cn.campusapp.updater.Updater;
import cn.campusapp.updater.UpdaterManager;

/**
 * A java source file generator using <a href="https://github.com/square/javapoet">javapoet</a>.
 * This generator will generate instance field updater for specified java class.
 * <p/>
 * For more information about javapoet, see <a href="https://github.com/square/javapoet">https://github.com/square/javapoet</a>
 * <p/>
 * Created by chen on 16/1/6.
 */
public class UpdaterGenerator {
    /**
     * Definition of first parameter of method {@link Updater#update(Object, Object)}
     */
    private final ParameterSpec mParamSpecOfOldEntity;
    /**
     * Definition of second parameter of method {@link Updater#update(Object, Object)}
     */
    private final ParameterSpec mParamSpecOfNewEntity;
    /**
     * Name of {@link Updater} with generic type parameter.
     * This is the 'extends' part of the updater class we are generating
     */
    private final TypeName mUpdaterNameWithTypeVariable;
    /**
     * List of updatable fields
     */
    private final List<Field> mFieldSpecList = new LinkedList<>();
    /**
     * Name of generated class
     */
    private final String mGeneratedClassName;
    /**
     * The enclosing package element of generated class
     */
    private final PackageElement mPackageElement;
    /**
     * The generated updater class is annotated with {@link UpdaterManager.ManagedUpdater}
     */
    private final AnnotationSpec mUpdaterAnnotation;
    private final boolean mIsTopClass;
    private final boolean mIsStatic;

    /**
     * Initialize necessary fields
     *
     * @param packageElement Package element which will be used while generating java source file
     * @param typeElement    The definition of a certain class for which we are generating updater
     */
    public UpdaterGenerator(PackageElement packageElement, TypeElement typeElement) {
        mPackageElement = packageElement;

        final ClassName entityClassName = ClassName.get(typeElement);
        /**
         * Generate parameter definitions of method {@link Updater#update(Object, Object)}
         */
        mParamSpecOfOldEntity = ParameterSpec.builder(entityClassName, "old" + typeElement.getSimpleName(), Modifier.FINAL).build();
        mParamSpecOfNewEntity = ParameterSpec.builder(entityClassName, "new" + typeElement.getSimpleName(), Modifier.FINAL).build();

        /**
         * the 'extends' part of generated updater class
         */
        mUpdaterNameWithTypeVariable = ParameterizedTypeName.get(ClassName.get(Updater.class), TypeVariableName.get(typeElement.asType()));

        /**
         * Class name of updater class
         */
        mGeneratedClassName = entityClassName.simpleName() + Updater.CLASS_PREFIX;

        mIsTopClass = typeElement.getNestingKind() == NestingKind.TOP_LEVEL;
        mIsStatic = typeElement.getModifiers().contains(Modifier.STATIC);

        mUpdaterAnnotation = AnnotationSpec.builder(UpdaterManager.ManagedUpdater.class)
                .addMember("value", "$T.class", typeElement)
                .build();
    }

    /**
     * Add a field for which we should generate update statement
     *
     * @param field Declaration of a field
     */
    @SuppressWarnings("UnusedDeclaration")
    public UpdaterGenerator addField(VariableElement field) {
        return addField(field, false);
    }


    /**
     * Add a field for which we should generate update statement
     *
     * @param field    Declaration of a field
     * @param omitNull Whether or not should null value be ignored when updating this field
     */
    public UpdaterGenerator addField(VariableElement field, boolean omitNull) {
        mFieldSpecList.add(Field.get(field, omitNull));
        return this;
    }

    /**
     * Add a property, the getter and setter will be used in its update statement
     *
     * @param property Declaration of a field
     * @param getter   Declaration of the field's getter method
     * @param setter   Declaration of the field's setter method
     * @param omitNull Whether or not should null value be ignored when updating this field
     */
    public UpdaterGenerator addProperty(VariableElement property, ExecutableElement getter, ExecutableElement setter, boolean omitNull) {
        mFieldSpecList.add(Property.get(property, getter, setter, omitNull));
        return this;
    }

    /**
     * Generate the update method
     *
     * @param methodName name of this method
     * @param oldTSpec   declaration of first parameter
     * @param newTSpec   declaration of second parameter
     * @return the definition of this method, including method signature, modifiers, method body and return type
     */
    private MethodSpec generateUpdateMethod(final String methodName, final ParameterSpec oldTSpec, final ParameterSpec newTSpec) {
        final List<CodeBlock> updateStatements = generateUpdaterBlocks(oldTSpec, newTSpec);
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(oldTSpec)
                .addParameter(newTSpec)
                .addAnnotation(Override.class)
                .addCode(generateNullCheckStatement(oldTSpec))
                .addCode(generateNullCheckStatement(newTSpec));

        for (CodeBlock updateStatement : updateStatements) {
            builder.addCode(updateStatement);
        }
        return builder.build();
    }

    /**
     * Generate null-check code block
     *
     * @param o the name to place in null-check statements,
     *          must be subtype of {@link CharSequence}, {@link ParameterSpec} or {@link FieldSpec}
     */
    private CodeBlock generateNullCheckStatement(final Object o) {
        if (o == null) {
            throw new IllegalArgumentException("o must not be null");
        }
        if (!(o instanceof CharSequence || o instanceof ParameterSpec || o instanceof FieldSpec)) {
            throw new IllegalArgumentException("expect name but was " + o);
        }
        return CodeBlock.builder().beginControlFlow("if ($N == null)", o)
                .addStatement("throw new $T(\"$N must not be null\")", IllegalArgumentException.class, o)
                .endControlFlow()
                .build();
    }

    /**
     * Generate update code blocks for every field
     *
     * @param oldName definition of first parameter, used to reference instance fields in code blocks
     * @param newName definition of second parameter, used to reference instance fields in code blocks
     * @return a list of code blocks which updates each field respectively
     */
    private List<CodeBlock> generateUpdaterBlocks(ParameterSpec oldName, ParameterSpec newName) {
        List<CodeBlock> codeBlocks = new ArrayList<>();
        for (Field field : mFieldSpecList) {
            codeBlocks.add(field.createUpdateStatement(oldName, newName));
        }
        return codeBlocks;
    }

    /**
     * Generate the definition of Updater Class:<br/>
     * <ol>
     * <li>This generated class is in the same package as the entity class(but not in same directory)</li>
     * <li>Name of the generated class(also the generated java source file) is the name of entity class + {@link Updater#CLASS_PREFIX}</li>
     * <li>The generated class is final, which means it cannot be extended</li>
     * </ol>
     *
     * @return The definition of generated updater class
     */
    private TypeSpec generateTypeSpec() {
        TypeSpec.Builder builder = TypeSpec.classBuilder(mGeneratedClassName);
        if (mIsTopClass || mIsStatic) {
            builder.addAnnotation(mUpdaterAnnotation);
        }
        return builder
                .addSuperinterface(mUpdaterNameWithTypeVariable)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(generateUpdateMethod("update", mParamSpecOfOldEntity, mParamSpecOfNewEntity))
                .build();
    }

    /**
     * Generate the definition of java source file
     */
    private JavaFile generateJavaFile() {
        final TypeSpec typeSpec = generateTypeSpec();

        return JavaFile.builder(mPackageElement.getQualifiedName().toString(), typeSpec)
                .build();
    }

    /**
     * Write generated java file definition to file system by calling {@link Filer#createSourceFile(CharSequence, Element...)}
     *
     * @param environment The processing environment
     */
    public void writeJavaFile(ProcessingEnvironment environment) throws IOException {
        JavaFile javaFile = generateJavaFile();

        JavaFileObject jfo = environment.getFiler().createSourceFile(mGeneratedClassName, mPackageElement);
        try (Writer writer = jfo.openWriter()) {
            javaFile.writeTo(writer);
            writer.flush();
        }
    }

    /**
     * A wrap of field declaration.
     * This class is responsible to generate update code block
     */
    private static class Field {
        /**
         * The abstraction from javapoet
         */
        public final FieldSpec mFieldSpec;
        /**
         * The omit null flag
         */
        public final boolean mIsOmitNull;

        public Field(FieldSpec fieldSpec, boolean omitNull) {
            mFieldSpec = fieldSpec;
            mIsOmitNull = omitNull;
        }

        /**
         * Wrap a declared field and create a new instance of type {@link Field}
         *
         * @param field      the field which can be updated
         * @param isOmitNull omit null flag
         * @return instance of {@link Field}
         */
        public static Field get(VariableElement field, boolean isOmitNull) {
            return new Field(
                    FieldSpec.builder(
                            TypeName.get(field.asType()),
                            field.getSimpleName().toString()
                    ).build(),
                    isOmitNull
            );
        }

        /**
         * Create update statement for wrapped field
         *
         * @param oldParam the parameter declaration used to reference fields in update statemens
         * @param newParam the parameter declaration used to reference fields in update statemens
         * @return generated code block for updating the wrapped field
         */
        public CodeBlock createUpdateStatement(ParameterSpec oldParam, ParameterSpec newParam) {
            final boolean checkNull = !mFieldSpec.type.isPrimitive() && mIsOmitNull;
            CodeBlock.Builder builder = CodeBlock.builder();
            if (checkNull) {
                builder.beginControlFlow("if ($N.$N != null)", newParam, mFieldSpec);
            }
            builder.addStatement("$N.$N = $N.$N", oldParam, mFieldSpec, newParam, mFieldSpec);
            if (checkNull) {
                builder.endControlFlow();
            }
            return builder.build();
        }
    }

    /**
     * A wrap of field declaration which getter and setter are both defined.
     * This class is responsible to generate update code block
     */
    private static class Property extends Field {
        /**
         * Getter method definition of wrapped field
         */
        public final MethodSpec mGetterSpec;
        /**
         * Setter method definition of wrapped field
         */
        public final MethodSpec mSetterSpec;

        public Property(FieldSpec fieldSpec, MethodSpec getter, MethodSpec setter, boolean omitNull) {
            super(fieldSpec, omitNull);
            mGetterSpec = getter;
            mSetterSpec = setter;
        }

        /**
         * Wrap a declared field and create a new instance of type {@link Field}
         *
         * @param field      the field which can be updated
         * @param getter     getter method definition
         * @param setter     setter method definition
         * @param isOmitNull omit null flag
         * @return instance of {@link Field}
         */
        public static Property get(VariableElement field, ExecutableElement getter, ExecutableElement setter, boolean isOmitNull) {
            return new Property(
                    FieldSpec.builder(
                            TypeName.get(field.asType()),
                            field.getSimpleName().toString()
                    ).build(),
                    MethodSpec.overriding(getter).build(),
                    MethodSpec.overriding(setter).build(),
                    isOmitNull
            );
        }

        /**
         * {@inheritDoc}
         * <br/>
         * The get/set operations are performed by calling getter/setter method
         */
        @Override
        public CodeBlock createUpdateStatement(ParameterSpec oldParam, ParameterSpec newParam) {
            final boolean checkNull = !mFieldSpec.type.isPrimitive() && mIsOmitNull;
            CodeBlock.Builder builder = CodeBlock.builder();
            if (checkNull) {
                builder.beginControlFlow("if (null != $N.$N())", newParam, mGetterSpec);
            }
            builder.addStatement("$N.$N($N.$N())", oldParam, mSetterSpec, newParam, mGetterSpec);
            if (checkNull) {
                builder.endControlFlow();
            }
            return builder.build();
        }
    }
}
