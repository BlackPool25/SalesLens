package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.batch.csv.CsvIngestionJobListener;
import com.shreyas.saleslens.batch.csv.StagingItemWriter;
import com.shreyas.saleslens.model.StagedRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.listener.CompositeJobExecutionListener;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepLocator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JdbcIngestionJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private JdbcItemReader jdbcItemReader;

    @Mock
    private JdbcItemProcessor jdbcItemProcessor;

    @Mock
    private StagingItemWriter stagingItemWriter;

    @Mock
    private CsvIngestionJobListener jobListener;

    private JdbcIngestionJobConfig config;

    @BeforeEach
    void setUp() {
        config = new JdbcIngestionJobConfig(
                jobRepository, transactionManager,
                jdbcItemReader, jdbcItemProcessor,
                stagingItemWriter, jobListener);
        // @Value field won't be injected in MockitoExtension test, so set it manually
        ReflectionTestUtils.setField(config, "chunkSize", 50);
    }

    @Test
    void jdbcIngestionJob_beanExists_hasCorrectName() {
        Job job = config.jdbcIngestionJob();

        assertThat(job).isNotNull();
        assertThat(job.getName()).isEqualTo("jdbcIngestionJob");
    }

    @Test
    void jdbcIngestionStep_beanExists_hasCorrectName() {
        Step step = config.jdbcIngestionStep();

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("jdbcIngestionStep");
    }

    @Test
    void job_hasCorrectStepAndListener() {
        Job job = config.jdbcIngestionJob();

        // Verify the step is registered in the job via StepLocator
        Step step = ((StepLocator) job).getStep("jdbcIngestionStep");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("jdbcIngestionStep");

        // Verify the listener is registered (access private field via reflection)
        Object compositeListenerObj = ReflectionTestUtils.getField(job, "listener");
        assertThat(compositeListenerObj).isInstanceOf(CompositeJobExecutionListener.class);
    }
}
