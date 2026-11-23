package com.portfolio.insurance.customer;

import com.portfolio.insurance.common.validation.TurkishIdentityNumbers;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

class CustomerIdentityValidator implements ConstraintValidator<ValidCustomerIdentity, CustomerRequest> {

    @Override
    public boolean isValid(CustomerRequest request, ConstraintValidatorContext context) {
        if (request == null || request.customerType() == null) {
            return true; // @NotNull on customerType reports this
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (request.customerType() == CustomerType.INDIVIDUAL) {
            if (!TurkishIdentityNumbers.isValidTckn(request.nationalId())) {
                valid = violation(context, "nationalId", "Geçerli bir T.C. kimlik numarası girin.");
            }
            if (!StringUtils.hasText(request.firstName())) {
                valid = violation(context, "firstName", "Ad zorunludur.");
            }
            if (!StringUtils.hasText(request.lastName())) {
                valid = violation(context, "lastName", "Soyad zorunludur.");
            }
            if (request.birthDate() == null) {
                valid = violation(context, "birthDate", "Doğum tarihi zorunludur.");
            }
        } else {
            if (!TurkishIdentityNumbers.isValidVkn(request.taxNumber())) {
                valid = violation(context, "taxNumber", "Geçerli bir vergi kimlik numarası girin.");
            }
            if (!StringUtils.hasText(request.companyName())) {
                valid = violation(context, "companyName", "Şirket unvanı zorunludur.");
            }
        }
        return valid;
    }

    private static boolean violation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        return false;
    }
}
