package cn.campusapp.updatercompiler.updater;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import cn.campusapp.updater.OmitNull;
import cn.campusapp.updater.Skip;
import cn.campusapp.updater.Updatable;

/**
 * A processor to generate field updater for classes annotated with {@link Updatable}.
 * Created by chen on 16/1/6.
 */
@SuppressWarnings("UnusedDeclaration")
@AutoService(Processor.class)
public class UpdaterProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Updatable.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * {@inheritDoc}
     * <p/>Handle only TypeElements which are annotated with {@link Updatable}.
     * Details are documented step by step in method body.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        /**
         * Since we only handle {@link Updatable}, we can just ignore the given set of annotations
         */
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Updatable.class);

        /**
         * Filter out type elements (which are class definitions)
         */
        Set<TypeElement> annotatedTypes = ElementFilter.typesIn(elements);

        for (final TypeElement typeElement : annotatedTypes) {
            /**
             * Find the package of typeElement
             */
            final PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);

            /**
             * List all declared elements in this type element
             */
            List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            /**
             * Filter out instance field declarations
             */
            final List<VariableElement> fields = ElementFilter.fieldsIn(enclosedElements);
            /**
             * And don't forget the instance methods among which we may search for getter/setter
             */
            final List<ExecutableElement> methods = ElementFilter.methodsIn(enclosedElements);
            /**
             * Instantiate {@link UpdaterGenerator}
             */
            final UpdaterGenerator updaterGenerator = new UpdaterGenerator(packageElement, typeElement);

            /**
             * Let's traverse instance field declarations
             */
            for (final VariableElement field : fields) {
                /**
                 * If this field is annotated with {@link Skip}, just ignore it
                 */
                if (field.getAnnotation(Skip.class) != null) {
                    continue;
                }
                final Set<Modifier> fieldModifiers = field.getModifiers();
                /**
                 * If this field is annotated with {@link OmitNull}, it will not receive a null value
                 */
                final boolean omitNull = field.getAnnotation(OmitNull.class) != null;
                ExecutableElement[] getterSetter = new ExecutableElement[2];
                /**
                 * Only non-static and non-final fields can be updated
                 */
                if (!fieldModifiers.contains(Modifier.FINAL) && !fieldModifiers.contains(Modifier.STATIC)) {
                    /**
                     * If both getter and setter of this field are declared, use its getter/setter for updating
                     */
                    if (cn.campusapp.updatercompiler.updater.ElementUtil.findGetterSetter(field, methods, getterSetter)) {
                        updaterGenerator.addProperty(field, getterSetter[0], getterSetter[1], omitNull);
                    }
                    /**
                     * If this field is non-protected and non-private, which means 'public' or 'package default', it is updatable
                     */
                    else if (!fieldModifiers.contains(Modifier.PROTECTED) && !fieldModifiers.contains(Modifier.PRIVATE)) {
                        updaterGenerator.addField(field, omitNull);
                    }
                }
            }

            try {
                /**
                 * Generate a new java source file
                 */
                updaterGenerator.writeJavaFile(processingEnv);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate updater");
            }
        }
        /**
         * Since {@link Updatable} is only claimed by this processor, return true
         */
        return true;
    }
}
