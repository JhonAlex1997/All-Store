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
import java.util.regex.Pattern;

/**
 * Texto que llega al cliente (en su correo, en el ticket o en el PDF): sin enlaces, sin etiquetas
 * HTML y sin caracteres de control. Así nadie puede usar el nombre de un cliente o una
 * observación para meter un enlace de phishing en un correo que sale desde el dominio de la
 * tienda. Los saltos de línea y tabulaciones sí se permiten.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextoSeguro.Validador.class)
public @interface TextoSeguro {

    String message() default "No se permiten enlaces (http://, www.), etiquetas HTML (< >) ni caracteres de control";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TextoSeguro, String> {

        private static final Pattern PROHIBIDO = Pattern.compile(
                "(?i)[a-z][a-z0-9+.-]*://|www\\.|[<>]|[\\p{Cntrl}&&[^\\n\\r\\t]]");

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            return valor == null || !PROHIBIDO.matcher(valor).find();
        }
    }
}
