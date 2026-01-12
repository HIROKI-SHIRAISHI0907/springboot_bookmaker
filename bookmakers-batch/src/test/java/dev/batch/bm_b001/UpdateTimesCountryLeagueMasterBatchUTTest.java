package dev.batch.bm_b001;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.logger.ManageLoggerComponent;

@ExtendWith(MockitoExtension.class)
class UpdateTimesCountryLeagueMasterBatchUTTest {

    @Mock
    ManageLoggerComponent manageLoggerComponent;

    @Mock
    jobExecControlIF jobExecControl;

    @Mock
    B001AsyncMasterPythonWorker asyncWorker;

    @InjectMocks
    UpdateTimesCountryLeagueMasterBatch target;

    @Test
    void execute_success_jobStartTrue_asyncCalled_returnSuccess() {
        try (MockedStatic<JobIdUtil> mocked = mockStatic(JobIdUtil.class)) {
            mocked.when(() -> JobIdUtil.generate("B001")).thenReturn("B001-00001");
            when(jobExecControl.jobStart("B001-00001", "B001")).thenReturn(true);

            int result = target.execute();

            assertEquals(BatchConstant.BATCH_SUCCESS, result);
            verify(jobExecControl).jobStart("B001-00001", "B001");
            verify(asyncWorker).run("B001-00001");
            verify(jobExecControl, never()).jobException(anyString());
        }
    }

    @Test
    void execute_error_jobStartFalse_asyncNotCalled_returnError() {
        try (MockedStatic<JobIdUtil> mocked = mockStatic(JobIdUtil.class)) {
            mocked.when(() -> JobIdUtil.generate("B001")).thenReturn("B001-00002");
            when(jobExecControl.jobStart("B001-00002", "B001")).thenReturn(false);

            int result = target.execute();

            assertEquals(BatchConstant.BATCH_ERROR, result);
            verify(jobExecControl).jobStart("B001-00002", "B001");
            verify(asyncWorker, never()).run(anyString());
            verify(jobExecControl, never()).jobException(anyString());
        }
    }

    @Test
    void execute_error_asyncThrows_thenJobExceptionCalled_returnError() {
        try (MockedStatic<JobIdUtil> mocked = mockStatic(JobIdUtil.class)) {
            mocked.when(() -> JobIdUtil.generate("B001")).thenReturn("B001-00003");
            when(jobExecControl.jobStart("B001-00003", "B001")).thenReturn(true);
            doThrow(new RuntimeException("boom")).when(asyncWorker).run("B001-00003");

            int result = target.execute();

            assertEquals(BatchConstant.BATCH_ERROR, result);
            verify(jobExecControl).jobStart("B001-00003", "B001");
            verify(asyncWorker).run("B001-00003");
            verify(jobExecControl).jobException("B001-00003");
        }
    }

    @Test
    void execute_error_jobStartThrows_thenJobExceptionNotCalled_returnError() {
        try (MockedStatic<JobIdUtil> mocked = mockStatic(JobIdUtil.class)) {
            mocked.when(() -> JobIdUtil.generate("B001")).thenReturn("B001-00004");
            when(jobExecControl.jobStart("B001-00004", "B001"))
                .thenThrow(new RuntimeException("db down"));

            int result = target.execute();

            assertEquals(BatchConstant.BATCH_ERROR, result);
            verify(jobExecControl).jobStart("B001-00004", "B001");
            verify(asyncWorker, never()).run(anyString());
            verify(jobExecControl, never()).jobException(anyString()); // jobInserted=false のまま
        }
    }

    @Test
    void execute_error_asyncThrows_andJobExceptionThrows_swallowed_returnError() {
        try (MockedStatic<JobIdUtil> mocked = mockStatic(JobIdUtil.class)) {
            mocked.when(() -> JobIdUtil.generate("B001")).thenReturn("B001-00005");
            when(jobExecControl.jobStart("B001-00005", "B001")).thenReturn(true);
            doThrow(new RuntimeException("boom")).when(asyncWorker).run("B001-00005");
            doThrow(new RuntimeException("jobException failed")).when(jobExecControl).jobException("B001-00005");

            int result = target.execute();

            assertEquals(BatchConstant.BATCH_ERROR, result);
            verify(jobExecControl).jobStart("B001-00005", "B001");
            verify(asyncWorker).run("B001-00005");
            verify(jobExecControl).jobException("B001-00005"); // 例外は握りつぶされるのでテストは落ちない
        }
    }
}
