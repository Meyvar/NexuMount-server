package cn.joker.webdav.webdav.adapter.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ParamAnnotation {

    String label();

    String type() default "el-input";

    ParamOption[] options() default {};

    boolean required() default true;
}
