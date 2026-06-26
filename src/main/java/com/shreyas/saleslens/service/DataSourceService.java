package com.shreyas.saleslens.service;

import com.shreyas.saleslens.dto.CreateSourceRequest;
import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.mapper.DataSourceMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.Users;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
        dataSource.setConnectionConfig(request.getConnectionConfig());
        dataSource.setCreatedBy(usersRepository.getReferenceById(userId));
        dataSourceRepository.save(dataSource);
        return dataSource.getName() + " saved successfully";
    }

    public Page<DataSourceResponse> getAllSources(Pageable pageable) {
        return dataSourceRepository.findAll(pageable).map(dataSourceMapper::toResponse);
    }

    public DataSourceResponse getBySourceId(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id).orElseThrow(
                () -> new RuntimeException(String.format("DataSource with id %s not found", id))
        );

        return dataSourceMapper.toResponse(dataSource);
    }

    public Page<DataSourceResponse> getByUser(Long userId, Pageable pageable) {
        Users user = usersRepository.getReferenceById(userId);
        return dataSourceRepository.findByCreatedBy(user, pageable).map(dataSourceMapper::toResponse);
    }
}
