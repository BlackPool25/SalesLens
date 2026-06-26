package com.shreyas.saleslens.mapper;

import com.shreyas.saleslens.dto.CanonicalCustomerDto;
import com.shreyas.saleslens.dto.CanonicalOrderDto;
import com.shreyas.saleslens.dto.CanonicalOrderLineItemDto;
import com.shreyas.saleslens.dto.CanonicalProductDto;
import com.shreyas.saleslens.dto.CanonicalRegionDto;
import com.shreyas.saleslens.dto.CanonicalSalespersonDto;
import com.shreyas.saleslens.model.CanonicalCustomer;
import com.shreyas.saleslens.model.CanonicalOrder;
import com.shreyas.saleslens.model.CanonicalOrderLineItem;
import com.shreyas.saleslens.model.CanonicalProduct;
import com.shreyas.saleslens.model.CanonicalRegion;
import com.shreyas.saleslens.model.CanonicalSalesperson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CanonicalMapper {

    @Mapping(target = "primarySourceId", source = "primarySource.id")
    CanonicalCustomerDto toDto(CanonicalCustomer customer);

    @Mapping(target = "primarySourceId", source = "primarySource.id")
    CanonicalProductDto toDto(CanonicalProduct product);

    @Mapping(target = "primarySourceId", source = "primarySource.id")
    CanonicalSalespersonDto toDto(CanonicalSalesperson salesperson);

    CanonicalRegionDto toDto(CanonicalRegion region);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "salespersonId", source = "salesperson.id")
    @Mapping(target = "regionId", source = "region.id")
    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "jobId", source = "job.id")
    CanonicalOrderDto toDto(CanonicalOrder order);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "productId", source = "product.id")
    CanonicalOrderLineItemDto toDto(CanonicalOrderLineItem lineItem);
}
