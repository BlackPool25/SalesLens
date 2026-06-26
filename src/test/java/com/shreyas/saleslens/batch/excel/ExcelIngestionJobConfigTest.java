package com.shreyas.saleslens.batch.excel;

import com.shreyas.saleslens.batch.csv.CsvIngestionJobListener;
import com.shreyas.saleslens.batch.csv.StagingItemWriter;
import com.shreyas.saleslens.model.StagedRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.listener.CompositeJobExecutionListener;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepLocator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExcelIngestionJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ExcelItemReader excelItemReader;

    @Mock
    private ExcelItemProcessor excelItemProcessor;

    @Mock
    private StagingItemWriter stagingItemWriter;

    @Mock
    private CsvIngestionJobListener jobListener;

    private ExcelIngestionJobConfig config;

    @BeforeEach
    void setUp() {
        config = new ExcelIngestionJobConfig(
                jobRepository, transactionManager,
                excelItemReader, excelItemProcessor,
                stagingItemWriter, jobListener);
        // @Value field won't be injected in MockitoExtension test, so set it manually
        ReflectionTestUtils.setField(config, "chunkSize", 50);
    }

    @Test
    void excelIngestionJob_beanExists_hasCorrectName() {
        Job job = config.excelIngestionJob();

        assertThat(job).isNotNull();
        assertThat(job.getName()).isEqualTo("excelIngestionJob");
    }

    @Test
    void excelIngestionStep_beanExists_hasCorrectName() {
        Step step = config.excelIngestionStep();

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("excelIngestionStep");
    }

    @Test
    void job_hasCorrectStepAndListener() {
        Job job = config.excelIngestionJob();

        // Verify the step is registered in the job via StepLocator
        Step step = ((StepLocator) job).getStep("excelIngestionStep");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("excelIngestionStep");

        // Verify the listener is registered (access private field via reflection)
        Object compositeListenerObj = ReflectionTestUtils.getField(job, "listener");
        assertThat(compositeListenerObj).isInstanceOf(CompositeJobExecutionListener.class);
    }
}
