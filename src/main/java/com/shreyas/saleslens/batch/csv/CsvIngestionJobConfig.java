package com.shreyas.saleslens.batch.csv;

import com.shreyas.saleslens.model.StagedRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CsvIngestionJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CsvItemProcessor csvItemProcessor;
    private final StagingItemWriter stagingItemWriter;
    private final CsvIngestionJobListener jobListener;

    @Value("${saleslens.batch.chunk-size:50}")
    private int chunkSize;

    @Bean
    public Job csvIngestionJob() {
        return new JobBuilder("csvIngestionJob", jobRepository)
                .listener(jobListener)
                .start(csvIngestionStep())
                .build();
    }

    @Bean
    public Step csvIngestionStep() {
        return new StepBuilder("csvIngestionStep", jobRepository)
                .<FieldSet, StagedRecord>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(csvReader(null))
                .processor(csvItemProcessor)
                .writer(stagingItemWriter)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<FieldSet> csvReader(
            @Value("#{jobParameters['filePath']}") String filePath) {

        List<String> headers = CsvHeaderDetector.detect(new FileSystemResource(filePath));

        return new FlatFileItemReaderBuilder<FieldSet>()
                .name("csvReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .quoteCharacter('"')
                .names(headers.toArray(new String[0]))
                .fieldSetMapper(fieldSet -> fieldSet)
                .build();
    }
}
