package org.docero.dgen.processor;

import org.docero.dgen.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings({"WeakerAccess", "unused"})
public class DGenClass {
    final private List<? extends AnnotationMirror> annotations;
    final private Name sourceName;
    final private String targetClassName;
    final private boolean isInterface;
    final private boolean isPackagePrivate;
    final private boolean unmodifiableType;
    final private Map<String, DGenProperty> properties = new HashMap<>();
    final private DGenDoc genDoc;
    private final TypeMirror superclass;
    private final List<? extends TypeMirror> interfaces;

    public static DGenClass readInterface(TypeElement prototype) {
        return new DGenClass(prototype, true);
    }

    public static DGenClass readBean(TypeElement prototype) {
        return new DGenClass(prototype, false);
    }

    private DGenClass(TypeElement prototype, boolean isInterface) {
        sourceName = prototype.getQualifiedName();
        targetClassName = prototype.getEnclosingElement()
                .getEnclosingElement().toString() + "." +
                prototype.getSimpleName();
        annotations = prototype.getAnnotationMirrors().stream()
                .filter(a -> !a.toString().startsWith("org.docero.dgen."))
                .collect(Collectors.toList());
        unmodifiableType = prototype.getAnnotation(DGenUnmodifiable.class) != null;
        if (isInterface) {
            DGenInterface ifAn = prototype.getAnnotation(DGenInterface.class);
            this.isInterface = true;
            isPackagePrivate = ifAn.packagePrivate();
        } else {
            DGenBean clAn = prototype.getAnnotation(DGenBean.class);
            this.isInterface = false;
            isPackagePrivate = clAn != null && clAn.packagePrivate();
        }
        superclass = prototype.getSuperclass();
        genDoc = prototype.getAnnotation(DGenDoc.class);
        interfaces = prototype.getInterfaces();

        for (Element element : prototype.getEnclosedElements()) {
            DGenProperty p = null;
            if (element.getKind() == ElementKind.FIELD)
                p = DGenProperty.from((VariableElement) element);
            else if (element.getKind() == ElementKind.METHOD)
                p = DGenProperty.from((ExecutableElement) element);
            if (p != null) {
                DGenProperty p0 = properties.get(p.getName());
                if (p0 != null) p0.join(p);
                else properties.put(p.getName(), p);
            }
        }
    }

    public List<? extends AnnotationMirror> getAnnotations() {
        return annotations;
    }

    public String getTargetClassName() {
        return targetClassName;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public boolean isPackagePrivate() {
        return isPackagePrivate;
    }

    public Collection<DGenProperty> getProperties() {
        return properties.values();
    }

    void generate(ProcessingEnvironment processingEnv) {
        int lastDot = targetClassName.lastIndexOf('.');
        String simpleName = targetClassName.substring(lastDot + 1);
        try {
            try (JavaClassWriter cw = new JavaClassWriter(processingEnv, targetClassName)) {
                cw.println("package " + targetClassName.substring(0, lastDot) + ";");
                cw.startBlock("/*");
                cw.println("This class is generated from " + sourceName);
                cw.println("by Docero Generation Library. Any modifications may be lost on compile stage!");
                cw.endBlock("*/");
                DGenProcessor.printDoc(cw, genDoc);
                DGenProcessor.printAnnotations(cw, annotations);

                if (!isInterface) {
                    cw.print((isPackagePrivate ? "" : "public ") + "class " + simpleName);

                    if (superclass.getKind() != TypeKind.NONE &&
                            !superclass.toString().equals("java.lang.Object"))
                        cw.print(" extends " + superclass.toString());

                    if (!interfaces.isEmpty())
                        cw.print(" implements " + interfaces.stream()
                                .map(TypeMirror::toString)
                                .collect(Collectors.joining(", ")));
                    cw.startBlock(" {");
                /*
                    Print properties and its access methods
                */
                    ArrayList<DGenProperty> unmodifiable = new ArrayList<>();
                    ArrayList<DGenProperty> builderProperties = new ArrayList<>();
                    for (DGenProperty property : properties.values()) {
                        if (property.isBuilderProperty()) builderProperties.add(property);
                        if (unmodifiableType || property.isUnmodifiable()) unmodifiable.add(property);
                        cw.println("");
                        cw.println(property.getModifiers() + " " +
                                (unmodifiableType || property.isUnmodifiable() ? "final " : "") +
                                property.getType() + " " + property.getName() + ";");
                        DGenProcessor.printDoc(cw, property.getGetterDoc());
                        DGenProcessor.printAnnotations(cw, property.getGetterAnnotations());
                        cw.println((isPackagePrivate ? "" : "public ") +
                                property.getType().toString() + " get" +
                                DGenProcessor.proper(property.getName()) +
                                "() {return " + property.getName() + ";}");
                        if (!(unmodifiableType || property.isUnmodifiable())) {
                            cw.println("");
                            DGenProcessor.printDoc(cw, property.getSetterDoc());
                            DGenProcessor.printAnnotations(cw, property.getSetterAnnotations());
                            cw.println((isPackagePrivate ? "" : "public ") + "void set" +
                                    DGenProcessor.proper(property.getName()) +
                                    "(" + property.getType().toString() + " val) {this." +
                                    property.getName() + " = val;}");
                        }
                    }
                /*
                    Create constructor for final (unmodifiable) properties
                */
                    if (!unmodifiable.isEmpty()) {
                        cw.println("");
                        cw.startBlock("public " + simpleName + "(" +
                                unmodifiable.stream()
                                        .map(var -> var.getType() + " " + var.getName())
                                        .collect(Collectors.joining(", ")) +
                                ") {");
                        Types tu = processingEnv.getTypeUtils();
                        for (DGenProperty var : unmodifiable) {
                            TypeMirror te = tu.erasure(var.getType());
                            if (var.isUnmodifiableCollection() && tu.isSameType(DGenProcessor.listType, te))
                                cw.println("this." + var.getName() + " = java.util.Collections.unmodifiableList(" +
                                        var.getName() + ");");
                            else if (var.isUnmodifiableCollection() && tu.isSameType(DGenProcessor.mapType, te))
                                cw.println("this." + var.getName() + " = java.util.Collections.unmodifiableMap(" +
                                        var.getName() + ");");
                            else
                                cw.println("this." + var.getName() + " = " + var.getName() + ";");
                        }
                        cw.endBlock("}");
                    }
                /*
                    Create abstract builder class for filling all unmodifiable properties,
                    and properties marked as DGenFromBuilder
                */
                    if (!builderProperties.isEmpty()) {
                        cw.println("");
                        cw.startBlock((isPackagePrivate ? "" : "public ") +
                                "static abstract class AbstractBuilder {");
                        // abstract methods for properties marked as DGenFromBuilder
                        for (DGenProperty val : builderProperties) {
                            cw.println("abstract " + val.getType() + " get" + DGenProcessor.proper(val.getName()) + "();");
                        }
                        // setters for unmodifiable properties
                        for (DGenProperty u : unmodifiable)
                            if (!builderProperties.contains(u)) {
                                cw.println("");
                                cw.println("private " + u.getType() + " " + u.getName() + ";");
                                DGenProcessor.printDoc(cw, u.getSetterDoc());
                                cw.println("public AbstractBuilder " + u.getName() + "(" +
                                        u.getType() + " val) {this." + u.getName() + " = val; return this;}");
                                cw.println((isPackagePrivate ? "" : "public ") +
                                        u.getType() + " get" + DGenProcessor.proper(u.getName()) + "() {return this." +
                                        u.getName() + ";}");
                            }
                        // build method
                        cw.println("");
                        cw.startBlock("public " + simpleName + " build() {");
                        cw.println(simpleName + " bean_ = new " + simpleName + "(" +
                                unmodifiable.stream()
                                        .map(u -> {
                                            if (builderProperties.contains(u))
                                                return "get" + DGenProcessor.proper(u.getName());
                                            else
                                                return "this." + u.getName();
                                        })
                                        .collect(Collectors.joining(", ")) +
                                ");");
                        for (DGenProperty builderProperty : builderProperties)
                            if (!unmodifiable.contains(builderProperty)) {
                                cw.println("bean_.set" + DGenProcessor.proper(builderProperty.getName()) +
                                        "(this.get" + DGenProcessor.proper(builderProperty.getName()) + "());");
                            }
                        cw.println("return bean_;");
                        cw.endBlock("}");
                        cw.endBlock("}");
                    }

                    cw.endBlock("}");
                } else {
                    cw.print((isPackagePrivate ? "" : "public ") + "interface " + simpleName);
                    if (!interfaces.isEmpty())
                        cw.print(" extends " + interfaces.stream()
                                .map(TypeMirror::toString)
                                .collect(Collectors.joining(", ")));
                    cw.startBlock(" {");

                    for (DGenProperty property : properties.values()) {
                        cw.println("");
                        DGenProcessor.printAnnotations(cw, property.getGetterAnnotations());
                        cw.println(property.getType().toString() + " get" +
                                DGenProcessor.proper(property.getName()) +
                                "();");
                        if (!(unmodifiableType || property.isUnmodifiable())) {
                            cw.println("");
                            DGenProcessor.printAnnotations(cw, property.getSetterAnnotations());
                            cw.println("void set" +
                                    DGenProcessor.proper(property.getName()) +
                                    "(" + property.getType().toString() + " val);");
                        }
                    }
                    cw.endBlock("}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
