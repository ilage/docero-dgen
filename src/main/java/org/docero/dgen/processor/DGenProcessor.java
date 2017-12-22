package org.docero.dgen.processor;

import org.docero.dgen.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Процессор анотаций.
 * <p>Подключение в Maven: &lt;annotationProcessor&gt;org.docero.dgen.DGenProcessor&lt;/annotationProcessor&gt;</p>
 * Created by i.vasyashin on 22.12.2017.
 */
@SupportedAnnotationTypes({
        "org.docero.dgen.DGenBean",
        "org.docero.dgen.DGenInterface"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DGenProcessor extends AbstractProcessor {
    private TypeMirror listType;
    private TypeMirror mapType;

    @Override
    public void init(ProcessingEnvironment environment) {
        super.init(environment);
        listType = environment.getTypeUtils().erasure(
                environment.getElementUtils().getTypeElement("java.util.List").asType()
        );
        mapType = environment.getTypeUtils().erasure(
                environment.getElementUtils().getTypeElement("java.util.Map").asType()
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> entities = roundEnv.getElementsAnnotatedWith(DGenInterface.class);
        entities.forEach(e -> generateInterface((TypeElement) e));

        entities = roundEnv.getElementsAnnotatedWith(DGenBean.class);
        entities.forEach(e -> generateBean((TypeElement) e));

        return false;
    }

    private void generateBean(TypeElement prototype) {
        try {
            DGenBean genBean = prototype.getAnnotation(DGenBean.class);
            boolean unmodifiableType = prototype.getAnnotation(DGenUnmodifiable.class) != null;

            Element packageElement = prototype;
            while (packageElement != null && packageElement.getKind() != ElementKind.PACKAGE) {
                packageElement = packageElement.getEnclosingElement();
            }
            assert packageElement != null;
            String fullPath = packageElement.toString() + "." + prototype.getSimpleName().toString();
            try (JavaClassWriter cw = new JavaClassWriter(processingEnv, fullPath)) {
                cw.println("package " + packageElement.toString() + ";");
                cw.startBlock("/*");
                cw.println("This class is generated from " + prototype.getQualifiedName());
                cw.println("by Docero Generation Library. Any modifications may be lost on compile stage!");
                cw.endBlock("*/");
                printDoc(cw, prototype.getAnnotation(DGenDoc.class));
                printAnnotations(cw, prototype.getAnnotationMirrors());
                cw.print((genBean.packagePrivate() ? "" : "public ") + "class " + prototype.getSimpleName());

                if (prototype.getSuperclass().getKind() != TypeKind.NONE &&
                        !prototype.getSuperclass().toString().equals("java.lang.Object"))
                    cw.print(" extends " + prototype.getSuperclass().toString());

                if (!prototype.getInterfaces().isEmpty())
                    cw.print(" implements " + prototype.getInterfaces().stream()
                            .map(TypeMirror::toString)
                            .collect(Collectors.joining(", ")));
                cw.startBlock(" {");
                /*
                    Print properties and its access methods
                */
                ArrayList<VariableElement> unmodifiable = new ArrayList<>();
                ArrayList<VariableElement> builderProperties = new ArrayList<>();
                for (Element element : prototype.getEnclosedElements()) {
                    if (element.getKind() == ElementKind.FIELD) {
                        VariableElement fieldElement = (VariableElement) element;
                        if (fieldElement.getAnnotation(DGenFromBuilder.class) != null)
                            builderProperties.add(fieldElement);
                        DGenUnmodifiable dgenUnmodifiable = fieldElement.getAnnotation(DGenUnmodifiable.class);
                        boolean unmodifiableProperty = dgenUnmodifiable != null;
                        if (unmodifiableType || unmodifiableProperty) unmodifiable.add(fieldElement);
                        cw.println("");
                        if (!fieldElement.getModifiers().isEmpty())
                            cw.print(fieldElement.getModifiers().stream()
                                    .map(Modifier::toString)
                                    .collect(Collectors.joining(" ")) + " ");
                        cw.println("private " +
                                (unmodifiableType || unmodifiableProperty ? "final " : "") +
                                fieldElement.asType().toString() + " " + fieldElement.getSimpleName() + ";");
                        printDoc(cw, fieldElement.getAnnotation(DGenDoc.class));
                        printAnnotations(cw, fieldElement.getAnnotationMirrors());
                        cw.println((genBean.packagePrivate() ? "" : "public ") +
                                fieldElement.asType().toString() + " get" +
                                proper(fieldElement.getSimpleName()) +
                                "() {return " + fieldElement.getSimpleName() + ";}");
                        if (!(unmodifiableType || unmodifiableProperty)) {
                            cw.println("");
                            printDoc(cw, fieldElement.getAnnotation(DGenDoc.class));
                            printAnnotations(cw, fieldElement.getAnnotationMirrors());
                            cw.println((genBean.packagePrivate() ? "" : "public ") + "void set" +
                                    proper(fieldElement.getSimpleName()) +
                                    "(" + fieldElement.asType().toString() + " val) {this." +
                                    fieldElement.getSimpleName() + " = val;}");
                        }
                    }
                }
                /*
                    Create constructor for final (unmodifiable) properties
                */
                if (!unmodifiable.isEmpty()) {
                    cw.println("");
                    cw.startBlock("public " + prototype.getSimpleName() + "(" +
                            unmodifiable.stream()
                                    .map(var -> var.asType() + " " + var.getSimpleName())
                                    .collect(Collectors.joining(", ")) +
                            ") {");
                    Types tu = processingEnv.getTypeUtils();
                    for (VariableElement var : unmodifiable) {
                        DGenUnmodifiable genUn = var.getAnnotation(DGenUnmodifiable.class);
                        boolean uc = genUn !=null && genUn.unmodifiableCollection();
                        TypeMirror te = tu.erasure(var.asType());
                        if (uc && tu.isSameType(listType, te))
                            cw.println("this." + var.getSimpleName() + " = java.util.Collections.unmodifiableList(" +
                                    var.getSimpleName() + ");");
                        else if (uc && tu.isSameType(mapType, te))
                            cw.println("this." + var.getSimpleName() + " = java.util.Collections.unmodifiableMap(" +
                                    var.getSimpleName() + ");");
                        else
                            cw.println("this." + var.getSimpleName() + " = " + var.getSimpleName() + ";");
                    }
                    cw.endBlock("}");
                }
                /*
                    Create abstract builder class for filling all unmodifiable properties,
                    and properties marked as DGenFromBuilder
                */
                if (!builderProperties.isEmpty()) {
                    cw.println("");
                    cw.startBlock((genBean.packagePrivate() ? "" : "public ") +
                            "static abstract class AbstractBuilder {");
                    // abstract methods for properties marked as DGenFromBuilder
                    for (VariableElement val : builderProperties) {
                        cw.println("abstract " + val.asType() + " get" + proper(val.getSimpleName()) + "();");
                    }
                    // setters for unmodifiable properties
                    for (VariableElement u : unmodifiable)
                        if (!builderProperties.contains(u)) {
                            cw.println("");
                            cw.println("private " + u.asType() + " " + u.getSimpleName() + ";");
                            printDoc(cw, u.getAnnotation(DGenDoc.class));
                            cw.println("public AbstractBuilder " + u.getSimpleName() + "(" +
                                    u.asType() + " val) {this." + u.getSimpleName() + " = val; return this;}");
                            cw.println((genBean.packagePrivate() ? "" : "public ") +
                                    u.asType() + " get" + proper(u.getSimpleName()) + "() {return this." +
                                    u.getSimpleName() + ";}");
                        }
                    // build method
                    cw.println("");
                    cw.startBlock("public " + prototype.getSimpleName() + " build() {");
                    cw.println(prototype.getSimpleName() + " bean_ = new " + prototype.getSimpleName() + "(" +
                            unmodifiable.stream()
                                    .map(u -> {
                                        if (builderProperties.contains(u))
                                            return "get" + proper(u.getSimpleName());
                                        else
                                            return "this." + u.getSimpleName();
                                    })
                                    .collect(Collectors.joining(", ")) +
                            ");");
                    for (VariableElement builderProperty : builderProperties)
                        if (!unmodifiable.contains(builderProperty)) {
                            cw.println("bean_.set" + proper(builderProperty.getSimpleName()) +
                                    "(this.get" + proper(builderProperty.getSimpleName()) + "());");
                        }
                    cw.println("return bean_;");
                    cw.endBlock("}");
                    cw.endBlock("}");
                }

                cw.endBlock("}");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printDoc(JavaClassWriter cw, DGenDoc genDoc) throws IOException {
        if (genDoc != null) {
            cw.startBlock("/**");
            for (String s : genDoc.value().split("\\R")) cw.println(s);
            cw.endBlock("**/");
        }
    }

    private void generateInterface(TypeElement prototype) {
        try {
            DGenInterface genInterface = prototype.getAnnotation(DGenInterface.class);
            boolean unmodifiableType = prototype.getAnnotation(DGenUnmodifiable.class) != null;

            Element packageElement = prototype;
            while (packageElement != null && packageElement.getKind() != ElementKind.PACKAGE) {
                packageElement = packageElement.getEnclosingElement();
            }
            assert packageElement != null;
            String fullPath = packageElement.toString() + "." + prototype.getSimpleName().toString();
            try (JavaClassWriter cw = new JavaClassWriter(processingEnv, fullPath)) {
                cw.println("package " + packageElement.toString() + ";");
                cw.startBlock("/*");
                cw.println("This class is generated from " + prototype.getQualifiedName());
                cw.println("by Docero Generation Library. Any modifications may be lost on compile stage!");
                cw.endBlock("*/");
                printAnnotations(cw, prototype.getAnnotationMirrors());
                cw.print((genInterface.packagePrivate() ? "" : "public ") + "interface " + prototype.getSimpleName());
                if (!prototype.getInterfaces().isEmpty())
                    cw.print(" extends " + prototype.getInterfaces().stream()
                            .map(TypeMirror::toString)
                            .collect(Collectors.joining(", ")));
                cw.startBlock(" {");

                for (Element element : prototype.getEnclosedElements()) {
                    if (element.getKind() == ElementKind.FIELD) {
                        VariableElement fieldElement = (VariableElement) element;
                        boolean unmodifiableProperty = fieldElement.getAnnotation(DGenUnmodifiable.class) != null;
                        cw.println("");
                        printAnnotations(cw, fieldElement.getAnnotationMirrors());
                        cw.println(fieldElement.asType().toString() + " get" +
                                proper(fieldElement.getSimpleName()) +
                                "();");
                        if (!(unmodifiableType || unmodifiableProperty)) {
                            cw.println("");
                            printAnnotations(cw, fieldElement.getAnnotationMirrors());
                            cw.println("void set" +
                                    proper(fieldElement.getSimpleName()) +
                                    "(" + fieldElement.asType().toString() + " val);");
                        }
                    }
                }

                cw.endBlock("}");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printAnnotations(JavaClassWriter cw, List<? extends AnnotationMirror> mirrors) throws IOException {
        for (AnnotationMirror m : mirrors)
            if (!m.getAnnotationType().toString().startsWith("org.docero.dgen.")) {
                cw.println(m.toString());
            }
    }

    private String proper(Name nm) {
        if (nm == null) return "";
        String v = nm.toString();
        if (v.length() > 1) return Character.toUpperCase(v.charAt(0)) + v.substring(1);
        else return v.toUpperCase();
    }
}
