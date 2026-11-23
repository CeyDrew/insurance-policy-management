package com.portfolio.insurance.policy;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PolicyMapper {

    @Mapping(target = "productCode", source = "product.code")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.displayName")
    @Mapping(target = "insuredObject", expression = "java(insuredObjectShort(policy))")
    PolicySummaryDto toSummary(Policy policy);

    @Mapping(target = "productId", source = "policy.product.id")
    @Mapping(target = "productCode", source = "policy.product.code")
    @Mapping(target = "productName", source = "policy.product.name")
    @Mapping(target = "customerId", source = "policy.customer.id")
    @Mapping(target = "customerName", source = "policy.customer.displayName")
    @Mapping(target = "customerIdentityNumber", source = "policy.customer.identityNumber")
    @Mapping(target = "vehicleId", source = "policy.vehicle.id")
    @Mapping(target = "propertyId", source = "policy.property.id")
    @Mapping(target = "insuredObject", expression = "java(insuredObjectLong(policy))")
    @Mapping(target = "agentName", source = "policy.agent.fullName")
    @Mapping(target = "renewedFromPolicyId", source = "policy.renewedFrom.id")
    @Mapping(target = "renewedFromPolicyNumber", source = "policy.renewedFrom.policyNumber")
    @Mapping(target = "installments", source = "installments")
    PolicyDto toDto(Policy policy, List<Installment> installments);

    InstallmentDto toDto(Installment installment);

    /** List view: plate number / address only, to avoid loading the property's city for every row. */
    default String insuredObjectShort(Policy policy) {
        if (policy.getVehicle() != null) {
            return policy.getVehicle().getPlateNumber();
        }
        if (policy.getProperty() != null) {
            return policy.getProperty().getAddressLine();
        }
        return policy.getCustomer().getDisplayName();
    }

    default String insuredObjectLong(Policy policy) {
        if (policy.getVehicle() != null) {
            return policy.getVehicle().getDescription();
        }
        if (policy.getProperty() != null) {
            return policy.getProperty().getDescription();
        }
        return policy.getCustomer().getDisplayName();
    }
}
