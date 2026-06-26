package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.batch.csv.CsvIngestionJobListener;
import com.shreyas.saleslens.batch.csv.StagingItemWriter;
import com.shreyas.saleslens.model.StagedRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class JdbcIngestionJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcItemReader jdbcItemReader;
    private final JdbcItemProcessor jdbcItemProcessor;
    private final StagingItemWriter stagingItemWriter;
    private final CsvIngestionJobListener jobListener;

    @Value("${saleslens.batch.chunk-size:50}")
    private int chunkSize;

    @Bean
    public Job jdbcIngestionJob() {
        return new JobBuilder("jdbcIngestionJob", jobRepository)
                .listener(jobListener)
                .start(jdbcIngestionStep())
                .build();
    }

    @Bean
    public Step jdbcIngestionStep() {
        return new StepBuilder("jdbcIngestionStep", jobRepository)
                .<Map<String, String>, StagedRecord>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(jdbcItemReader)
                .processor(jdbcItemProcessor)
                .writer(stagingItemWriter)
                .build();
    }
}
