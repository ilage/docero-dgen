package org.docero.dgen.processor;

import org.docero.dgen.DGenBean;
import org.docero.dgen.DGenDoc;
import org.docero.dgen.DGenInterface;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.List;
import java.util.Set;

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
    static TypeMirror listType;
    static TypeMirror mapType;

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
        entities.forEach(e -> DGenClass.readInterface((TypeElement) e).generate(processingEnv));

        entities = roundEnv.getElementsAnnotatedWith(DGenBean.class);
        entities.forEach(e -> DGenClass.readBean((TypeElement) e).generate(processingEnv));

        return false;
    }

    static void printDoc(JavaClassWriter cw, DGenDoc genDoc) throws IOException {
        if (genDoc != null) {
            cw.startBlock("/**");
            for (String s : genDoc.value().split("\\R")) cw.println(s);
            cw.endBlock("**/");
        }
    }

    static void printAnnotations(JavaClassWriter cw, List<? extends AnnotationMirror> mirrors) throws IOException {
        for (AnnotationMirror m : mirrors)
            if (!m.getAnnotationType().toString().startsWith("org.docero.dgen.")) {
                cw.println(m.toString());
            }
    }

    static String proper(String v) {
        if (v.length() > 1) return Character.toUpperCase(v.charAt(0)) + v.substring(1);
        else return v.toUpperCase();
    }
}
