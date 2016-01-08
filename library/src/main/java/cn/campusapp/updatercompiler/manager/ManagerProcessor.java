package cn.campusapp.updatercompiler.manager;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import cn.campusapp.updater.UpdaterManager;

/**
 * A processor to generate UpdaterManager instance.
 * Created by chen on 16/1/7.
 */
@SuppressWarnings("UnusedDeclaration")
@AutoService(Processor.class)
public class ManagerProcessor extends AbstractProcessor {
    private static TypeMirror getAnnotationValue(UpdaterManager.ManagedUpdater updaterAnnotation) {
        try {
            updaterAnnotation.value();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        throw new AssertionError();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(UpdaterManager.ManagedUpdater.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * {@inheritDoc}
     * <p/>Handle only TypeElements which are annotated with {@link UpdaterManager.ManagedUpdater}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(UpdaterManager.ManagedUpdater.class);
        Set<TypeElement> updaterClasses = ElementFilter.typesIn(elements);
        if (updaterClasses.isEmpty()) {
            return false;
        }

        PackageElement packageElement = processingEnv.getElementUtils().getPackageElement(UpdaterManager.class.getPackage().getName());
        final ManagerGenerator generator = new ManagerGenerator(packageElement);

        for (final TypeElement updaterClass : updaterClasses) {
            final UpdaterManager.ManagedUpdater managedUpdater = updaterClass.getAnnotation(UpdaterManager.ManagedUpdater.class);
            TypeMirror typeMirror = getAnnotationValue(managedUpdater);
            generator.put(typeMirror, updaterClass);
        }

        try {
            generator.writeToSource(processingEnv);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate updater manager" + e.toString());
        }
        return true;
    }
}
