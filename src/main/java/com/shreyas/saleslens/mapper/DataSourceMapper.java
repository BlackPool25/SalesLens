package com.shreyas.saleslens.mapper;

import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.model.DataSource;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DataSourceMapper {

    @Mapping(target = "createdBy", source = "createdBy.id")
    DataSourceResponse toResponse(DataSource dataSource);
}
