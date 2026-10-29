package com.allstore.api.common.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Largo máximo en bytes (UTF-8), no en caracteres. Para las contraseñas: BCrypt solo admite 72
 * bytes y una letra con tilde ocupa 2, así que 72 caracteres con tildes no entran (fallaba con un
 * error interno al guardar la contraseña).
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BytesMaximos.Validador.class)
public @interface BytesMaximos {

    int value();

    String message() default "Es demasiado largo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<BytesMaximos, String> {

        private int maximo;

        @Override
        public void initialize(BytesMaximos anotacion) {
            this.maximo = anotacion.value();
        }

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= maximo;
        }
    }
}
