package cn.campusapp.updatercompiler.updater;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

/**
 * A collection of some useful methods while processing {@link Element}
 * <p/>
 * Created by chen on 16/1/7.
 */
final class ElementUtil {
    static final Pattern sFIELD_START_WITH_M = Pattern.compile("\\Am(?<name>[A-Z]\\w*)\\Z");
    static final Pattern sBOOLEAN_FIELD = Pattern.compile("\\A(?:mIs|is)(?<name>\\w+)\\Z");

    private ElementUtil() {
    }

    /**
     * For specified field, try to find its getter/setter in given methods.
     *
     * @param field        in-parameter, the specified field
     * @param methods      in-parameter, the given method list in which we try to find getter and setter
     * @param getterSetter out-parameter, array of type {@link ExecutableElement} which is used to pass getter/setter out
     * @return true if both getter and setter are found, otherwise false
     * @throws IllegalArgumentException if getterSetter is null or its length is not 2
     */
    static boolean findGetterSetter(/*in*/final VariableElement field,
                                    /*in*/final List<ExecutableElement> methods,
                                    /*out*/final ExecutableElement[] getterSetter) {
        if (getterSetter == null || getterSetter.length != 2) {
            throw new IllegalArgumentException("Size of out param getterSetter must be 2");
        }
        final boolean isBoolean = field.asType().getKind() == TypeKind.BOOLEAN;
        final String getterPrefix = isBoolean ? "is" : "get";
        final String setterPrefix = "set";

        Name fieldName = field.getSimpleName();
        Matcher matcher = isBoolean ? sBOOLEAN_FIELD.matcher(fieldName) : sFIELD_START_WITH_M.matcher(fieldName);
        CharSequence qualifiedName;
        if (matcher.find()) {
            qualifiedName = matcher.group("name");
        } else {
            qualifiedName = capitalizeFirstLetter(fieldName);
        }

        final String getterName = getterPrefix + qualifiedName;
        final String setterName = setterPrefix + qualifiedName;

        boolean getterFound = false;
        boolean setterFound = false;
        for (int i = 0; i < methods.size() && !(getterFound && setterFound); i++) {
            final ExecutableElement method = methods.get(i);
            if (!getterFound && method.getSimpleName().contentEquals(getterName)) {
                getterSetter[0] = method;
                getterFound = true;
            } else if (!setterFound && method.getSimpleName().contentEquals(setterName)) {
                getterSetter[1] = method;
                setterFound = true;
            }
        }

        return getterFound && setterFound;
    }

    /**
     * Capitalize the first letter of given {@link CharSequence}.
     * If the first letter is already capitalized, the char sequence is returned directly.
     *
     * @param chars chars to be processed
     * @return char sequence with first letter capitalized
     */
    static CharSequence capitalizeFirstLetter(final CharSequence chars) {
        final char first = chars.charAt(0);
        if (Character.isUpperCase(first)) {
            return chars;
        } else {
            CharSequence following = chars.subSequence(1, chars.length());
            return String.valueOf(Character.toUpperCase(first)) + following;
        }
    }

    static boolean isAnnotationPresent(VariableElement element, String annotationName) {
        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
        for (final AnnotationMirror annotationMirror : annotationMirrors) {
            if (((TypeElement) annotationMirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationName)) {
                return true;
            }
        }
        return false;
    }
}
