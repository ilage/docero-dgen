package org.docero.dgen.tests;

import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestsConfig.class)
@ActiveProfiles("test")
public class Test {
    @org.junit.Test
    public void test1() {
        SimpleBeanBuilder builder = new SimpleBeanBuilder(1, 2);
        SimpleBean bean = builder.f1(10).build();
        assertEquals(10, bean.getF1());
        assertEquals(Integer.valueOf(1), bean.getF2());
        assertEquals(Long.valueOf(2), bean.getF4());
    }
}
