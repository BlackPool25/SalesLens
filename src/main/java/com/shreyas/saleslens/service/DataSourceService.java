package com.shreyas.saleslens.service;

import com.shreyas.saleslens.dto.CreateSourceRequest;
import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.mapper.DataSourceMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final DataSourceRepository dataSourceRepository;
    private final UsersRepository usersRepository;
    private final DataSourceMapper dataSourceMapper;

    public String createSource(CreateSourceRequest request, Long userId) {
        DataSource dataSource = new DataSource();
        dataSource.setName(request.getName());
        dataSource.setSourceType(request.getSourceType());
        dataSource.setTrustScore(request.getTrustScore());
        dataSource.setActive(request.getActive());
        dataSource.setCreatedBy(usersRepository.getReferenceById(userId));
        dataSourceRepository.save(dataSource);
        return dataSource.getName() + " saved successfully";
    }

    public List<DataSourceResponse> getAllSources() {
        List<DataSource> dataSourceList = dataSourceRepository.findAll();

        List<DataSourceResponse> dataSourceResponseList = new ArrayList<>();
        for (DataSource dataSource : dataSourceList) {
            dataSourceResponseList.add(dataSourceMapper.toResponse(dataSource));
        }

        return dataSourceResponseList;
    }
}
