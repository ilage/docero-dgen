package org.docero.dgen.processor;

import org.docero.dgen.DGenDoc;
import org.docero.dgen.DGenFromBuilder;
import org.docero.dgen.DGenUnmodifiable;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings({"unused", "WeakerAccess"})
public class DGenProperty {
    final private String name;
    final private TypeMirror type;
    final private Prototypes prototype;
    private List<? extends AnnotationMirror> annotations;
    private List<? extends AnnotationMirror> getterAnnotations;
    private List<? extends AnnotationMirror> setterAnnotations;
    private String modifiers;
    private boolean hasSetter;
    private boolean hasGetter;
    private boolean isBuilderProperty;
    private boolean isUnmodifiable;
    private boolean isUnmodifiableCollection;
    private DGenDoc genDoc;
    private DGenDoc setterDoc;
    private DGenDoc getterDoc;
    private Element element;

    public boolean isHasSetter() {
        return hasSetter;
    }

    public boolean isHasGetter() {
        return hasGetter;
    }

    enum Prototypes {GETTER, SETTER, VARIABLE}

    static DGenProperty from(VariableElement fieldElement) {
        return new DGenProperty(fieldElement,
                fieldElement.getSimpleName().toString(),
                fieldElement.asType(), Prototypes.VARIABLE);
    }

    static DGenProperty from(ExecutableElement method) {
        String name;
        TypeMirror type;
        Prototypes prototype = Prototypes.GETTER;
        String sn = method.getSimpleName().toString();
        if (sn.startsWith("get") || sn.startsWith("has")) {
            name = Character.toLowerCase(sn.charAt(3)) + sn.substring(4);
            type = method.getReturnType();
        } else if (sn.startsWith("set")) {
            name = Character.toLowerCase(sn.charAt(3)) + sn.substring(4);
            type = method.getTypeParameters().size() != 1 ? null :
                    method.getTypeParameters().get(0).asType();
            prototype = Prototypes.SETTER;
        } else if (sn.startsWith("is")) {
            name = Character.toLowerCase(sn.charAt(2)) + sn.substring(3);
            type = method.getReturnType();
        } else {
            name = sn;
            type = method.getReturnType();
        }
        return new DGenProperty(method, name, type, prototype);
    }

    private DGenProperty(Element element, String name, TypeMirror type, Prototypes prototype) {
        this.element = element;
        this.prototype = prototype;
        this.name = name;
        this.type = type;
        isBuilderProperty = element.getAnnotation(DGenFromBuilder.class) != null;
        DGenUnmodifiable dgenUnmodifiable = element.getAnnotation(DGenUnmodifiable.class);
        isUnmodifiable = dgenUnmodifiable != null;
        isUnmodifiableCollection = isUnmodifiable && dgenUnmodifiable.unmodifiableCollection();
        switch (prototype) {
            case GETTER:
                getterAnnotations = element.getAnnotationMirrors().stream()
                        .filter(a -> !a.getAnnotationType().toString().startsWith("org.docero.gen."))
                        .collect(Collectors.toList());
                setterAnnotations = new ArrayList<>();
                annotations = new ArrayList<>();
                modifiers = "";
                getterDoc = element.getAnnotation(DGenDoc.class);
                setterDoc = null;
                genDoc = null;
                hasSetter = false;
                hasGetter = true;
                break;
            case SETTER:
                setterAnnotations = element.getAnnotationMirrors().stream()
                        .filter(a -> !a.getAnnotationType().toString().startsWith("org.docero.gen."))
                        .collect(Collectors.toList());
                getterAnnotations = new ArrayList<>();
                annotations = new ArrayList<>();
                modifiers = "";
                setterDoc = element.getAnnotation(DGenDoc.class);
                getterDoc = null;
                genDoc = null;
                hasSetter = true;
                hasGetter = false;
                break;
            default:
                annotations = element.getAnnotationMirrors().stream()
                        .filter(a -> !a.getAnnotationType().toString().startsWith("org.docero.gen."))
                        .collect(Collectors.toList());
                getterAnnotations = new ArrayList<>();
                setterAnnotations = new ArrayList<>();
                if (!element.getModifiers().isEmpty()) {
                    modifiers = element.getModifiers().stream()
                            .map(Modifier::toString)
                            .filter(m -> "transient".equals(m) || "protected".equals(m))
                            .collect(Collectors.joining(" "));
                    if (modifiers.isEmpty())
                        modifiers = "private";
                    else if (!modifiers.contains("protected"))
                        modifiers = "private " + modifiers;
                } else
                    modifiers = "private";

                genDoc = element.getAnnotation(DGenDoc.class);
                setterDoc = null;
                getterDoc = null;
                hasSetter = true;
                hasGetter = true;
        }
    }

    void join(DGenProperty property) {
        modifiers = modifiers.isEmpty() ? property.modifiers : modifiers;
        isBuilderProperty = isBuilderProperty || property.isBuilderProperty;
        isUnmodifiable = isUnmodifiable || property.isUnmodifiable;
        isUnmodifiableCollection = isUnmodifiableCollection || property.isUnmodifiableCollection;
        switch (property.prototype) {
            case GETTER:
                hasGetter = true;
                setterDoc = property.setterDoc;
                getterAnnotations = property.getterAnnotations;
                if (getterAnnotations.size() > Math.max(setterAnnotations.size(), annotations.size()))
                    element = property.element;
                break;
            case SETTER:
                hasSetter = true;
                getterDoc = property.getterDoc;
                setterAnnotations = property.setterAnnotations;
                if (setterAnnotations.size() > Math.max(annotations.size(), getterAnnotations.size()))
                    element = property.element;
                break;
            default:
                annotations = property.annotations;
                genDoc = property.genDoc;
                if (annotations.size() > Math.max(setterAnnotations.size(), getterAnnotations.size()))
                    element = property.element;
        }
    }

    public boolean isBuilderProperty() {
        return isBuilderProperty;
    }

    public boolean isUnmodifiable() {
        return isUnmodifiable;
    }

    public boolean isUnmodifiableCollection() {
        return isUnmodifiableCollection;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }

    public DGenDoc getGetterDoc() {
        return getterDoc == null ? genDoc : getterDoc;
    }

    public DGenDoc getSetterDoc() {
        return setterDoc == null ? genDoc : setterDoc;
    }

    public List<? extends AnnotationMirror> getGetterAnnotations() {
        return concat(annotations, getterAnnotations);
    }

    public List<? extends AnnotationMirror> getSetterAnnotations() {
        return concat(annotations, setterAnnotations);
    }

    @SuppressWarnings("unchecked")
    private List<? extends AnnotationMirror> concat(List<? extends AnnotationMirror> l1, List<? extends AnnotationMirror> l2) {
        ArrayList al = new ArrayList(l1);
        al.addAll(l2);
        return al;
    }

    /**
     * used only for bean implementation
     *
     * @return string with modifiers for class field
     */
    public String getModifiers() {
        return modifiers;
    }

    public Element getElement() {
        return element;
    }
}
