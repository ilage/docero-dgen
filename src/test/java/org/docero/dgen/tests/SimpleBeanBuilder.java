package org.docero.dgen.tests;

public class SimpleBeanBuilder extends SimpleBean.AbstractBuilder {
    @Override
    Integer getF2() {
        return f2;
    }

    @Override
    Long getF4() {
        return f4;
    }

    private final int f2;
    private final long f4;

    public SimpleBeanBuilder(int f2, long f4) {
        this.f2 = f2;
        this.f4 = f4;
    }
}
