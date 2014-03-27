package net.mrkzea.mockserver;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)

public @interface MockResponse {
    String url();
    String response();
    int statusCode() default 200;
    String contentType() default "application/json";

}
