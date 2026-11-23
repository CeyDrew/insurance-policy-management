package com.portfolio.insurance.customer;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Type-dependent rules: individuals need a valid TCKN, name and birth date; companies a valid VKN and title.
 * Violations are reported on the offending field so the form can show them inline.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CustomerIdentityValidator.class)
public @interface ValidCustomerIdentity {

    String message() default "Müşteri kimlik bilgileri geçersiz.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
