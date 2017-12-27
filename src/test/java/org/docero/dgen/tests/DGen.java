package org.docero.dgen.tests;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.docero.dgen.*;

import java.io.Serializable;
import java.util.List;

@SuppressWarnings("unused")
abstract class DGen {
    @DGenBean
    @DGenDoc("<p>Это тестовый класс<p>")
    abstract class SimpleBean implements Serializable, org.docero.dgen.tests.SimpleInterface {
        @DGenUnmodifiable
        @DGenDoc("поле 1\n<p>идентификатор</p>")
        int f1;
        @DGenFromBuilder
        @DGenDoc("поле 2")
        Integer f2;
        @DGenDoc("поле 3")
        long f3;
        @DGenFromBuilder
        @DGenDoc("поле 4")
        Long f4;
        @DGenDoc("поле 5")
        String fs;
        @JsonIgnore
        @DGenDoc("поле 6")
        transient List<String> fsl;
    }

    @DGenInterface(packagePrivate = true)
    @DGenDoc("<p>Это тестовый интерфейс<p>")
    abstract class SimpleInterface implements Serializable {
        @DGenUnmodifiable
        @DGenDoc("поле 1\n<p>идентификатор</p>")
        int f1;
        @DGenFromBuilder
        @DGenDoc("поле 2")
        Integer f2;
        @DGenDoc("поле 3")
        long f3;
        @DGenFromBuilder
        @DGenDoc("поле 4")
        Long f4;
        @DGenDoc("поле 5")
        String fs;
        @DGenDoc("поле 6")
        transient List<String> fsl;
        @JsonIgnore
        abstract List<String> getFsl();
    }
}
