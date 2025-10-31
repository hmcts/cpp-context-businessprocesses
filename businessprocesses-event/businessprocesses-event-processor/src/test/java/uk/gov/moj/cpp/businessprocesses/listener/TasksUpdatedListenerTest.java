package uk.gov.moj.cpp.businessprocesses.listener;

import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.shared.TaskAuditService;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static java.time.LocalDate.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_DUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.PREVIOUS_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.shared.ProcessVariableConstants.WORK_QUEUE;


@SuppressWarnings({"squid:S2187"})
@ExtendWith(MockitoExtension.class)
public class TasksUpdatedListenerTest {

    @Mock
    protected Sender sender;

    @InjectMocks
    protected TasksUpdatedListener sut;

    @Mock
    protected SystemUserProvider systemUserProvider;

    @Mock
    protected TaskAuditService taskAuditService;

    @Captor
    protected ArgumentCaptor<JsonEnvelope> jsonEnvelopeArgumentCaptor;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");


    @Test
    public void shouldTriggerTasksUpdatedListenerWhenDueDateChangedAndWorkFlowNotChanged() {
        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(UUID.randomUUID()));

        final String userID = randomUUID().toString();
        final String workQueue = randomUUID().toString();
        final String previousDueDate = dateFormat.format(Date.from(now().atStartOfDay().toInstant(ZoneOffset.UTC)));
        final Date dueDate = Date.from(now().plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC));
        final VariableMapImpl variableMap = new VariableMapImpl();
        variableMap.put(PREVIOUS_DUE, previousDueDate);
        variableMap.put(LAST_UPDATED_BY_ID, userID);
        variableMap.put(LAST_UPDATED_BY_NAME, "User updated");
        variableMap.put(PREVIOUS_WORK_QUEUE, workQueue);
        variableMap.put(WORK_QUEUE, workQueue);

        final TaskEntity delegateTask = Mockito.spy(new TaskEntity());
        doReturn(variableMap).when(delegateTask).getVariablesLocal();
        doReturn(userID).when(delegateTask).getVariableLocal(LAST_UPDATED_BY_ID);
        doReturn("User updated").when(delegateTask).getVariableLocal(LAST_UPDATED_BY_NAME);

        delegateTask.setDueDate(dueDate);
        delegateTask.setId(UUID.randomUUID().toString());
        delegateTask.setName("Task name");

        sut.notify(delegateTask);

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());
        verify(taskAuditService, times(1)).audit(jsonEnvelopeArgumentCaptor.capture(), eq(userID));

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-updated"));
    }

    @Test
    public void shouldTriggerTasksUpdatedListenerWhenDueDateNotChangedAndWorkQueueChanged() {
        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(UUID.randomUUID()));

        final String userID = randomUUID().toString();
        final String previousWorkQueue = randomUUID().toString();
        final String workQueue = randomUUID().toString();
        final Date dueDate = Date.from(now().atStartOfDay().toInstant(ZoneOffset.UTC));
        final String previousDueDate = dateFormat.format(dueDate);
        final VariableMapImpl variableMap = new VariableMapImpl();
        variableMap.put(PREVIOUS_DUE, previousDueDate);
        variableMap.put(LAST_UPDATED_BY_ID, userID);
        variableMap.put(LAST_UPDATED_BY_NAME, "User updated");
        variableMap.put(PREVIOUS_WORK_QUEUE, previousWorkQueue);
        variableMap.put(WORK_QUEUE, workQueue);

        final TaskEntity delegateTask = Mockito.spy(new TaskEntity());

        doReturn(userID).when(delegateTask).getVariableLocal(LAST_UPDATED_BY_ID);
        doReturn("User updated").when(delegateTask).getVariableLocal(LAST_UPDATED_BY_NAME);
        doReturn(variableMap).when(delegateTask).getVariablesLocal();

        delegateTask.setDueDate(dueDate);
        delegateTask.setId(UUID.randomUUID().toString());
        delegateTask.setName("Task name");

        sut.notify(delegateTask);

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());
        verify(taskAuditService, times(1)).audit(jsonEnvelopeArgumentCaptor.capture(), eq(userID));

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-updated"));
    }

    @Test
    public void shouldTriggerTasksUpdatedListenerWhenDueDateAndWorkQueueChanged() {
        when(systemUserProvider.getContextSystemUserId()).thenReturn(Optional.of(UUID.randomUUID()));

        final String userID = randomUUID().toString();
        final String previousWorkQueue = randomUUID().toString();
        final String workQueue = randomUUID().toString();
        final String previousDueDate = dateFormat.format(Date.from(now().atStartOfDay().toInstant(ZoneOffset.UTC)));
        final Date dueDate = Date.from(now().plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC));
        final VariableMapImpl variableMap = new VariableMapImpl();
        variableMap.put(PREVIOUS_DUE, previousDueDate);
        variableMap.put(LAST_UPDATED_BY_ID, userID);
        variableMap.put(LAST_UPDATED_BY_NAME, "User updated");
        variableMap.put(PREVIOUS_WORK_QUEUE, previousWorkQueue);
        variableMap.put(WORK_QUEUE, workQueue);

        final TaskEntity delegateTask = Mockito.spy(new TaskEntity());

        doReturn(userID).when(delegateTask).getVariableLocal(LAST_UPDATED_BY_ID);
        doReturn("User updated").when(delegateTask).getVariableLocal(LAST_UPDATED_BY_NAME);
        doReturn(variableMap).when(delegateTask).getVariablesLocal();


        delegateTask.setDueDate(dueDate);
        delegateTask.setId(UUID.randomUUID().toString());
        delegateTask.setName("Task name");

        sut.notify(delegateTask);

        verify(sender, times(1)).sendAsAdmin(jsonEnvelopeArgumentCaptor.capture());
        verify(taskAuditService, times(1)).audit(jsonEnvelopeArgumentCaptor.capture(), eq(userID));

        final Metadata metadata = jsonEnvelopeArgumentCaptor.getValue().metadata();
        assertThat(metadata.name(), is("businessprocesses.command.record-task-updated"));
    }

    @Test
    public void shouldNotTriggerTasksUpdatedListenerWhenDueDateAndWorkFlowNotChanged() {

        final String userID = randomUUID().toString();
        final Date dueDate = Date.from(now().atStartOfDay().toInstant(ZoneOffset.UTC));
        final String previousDueDate = dateFormat.format(dueDate);
        final String workQueue = randomUUID().toString();
        final VariableMapImpl variableMap = new VariableMapImpl();
        variableMap.put(PREVIOUS_DUE, previousDueDate);
        variableMap.put(LAST_UPDATED_BY_ID, userID);
        variableMap.put(LAST_UPDATED_BY_NAME, "User updated");
        variableMap.put(PREVIOUS_WORK_QUEUE, workQueue);
        variableMap.put(WORK_QUEUE, workQueue);

       // final VariableMapImpl variableMapLocal = new VariableMapImpl();

        final TaskEntity delegateTask = Mockito.spy(new TaskEntity());
        doReturn(variableMap).when(delegateTask).getVariablesLocal();


        delegateTask.setDueDate(dueDate);
        delegateTask.setId(UUID.randomUUID().toString());
        delegateTask.setName("Task name");

        sut.notify(delegateTask);

        verify(sender, times(0)).sendAsAdmin(any());
        verify(taskAuditService, times(0)).audit(any(), any());

    }

}