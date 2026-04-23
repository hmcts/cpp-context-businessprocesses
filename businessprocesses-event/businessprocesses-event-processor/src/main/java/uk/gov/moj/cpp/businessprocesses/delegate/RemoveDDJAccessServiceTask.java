package uk.gov.moj.cpp.businessprocesses.delegate;

import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createArrayBuilder;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;
import static uk.gov.justice.services.messaging.Envelope.metadataBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.ACTION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.CASE_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.DESCRIPTION;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.OBJECT;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PERMISSIONS;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.PERMISSION_ID;
import static uk.gov.moj.cpp.businessprocesses.shared.Constants.TARGET;

import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.featurecontrol.FeatureControlGuard;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.core.sender.Sender;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;

@Named("removeDDJAccessServiceTask")
public class RemoveDDJAccessServiceTask implements JavaDelegate {

    private static final Logger LOGGER = getLogger(RemoveDDJAccessServiceTask.class);

    private static final String PERMISSIONS_QUERY_API = "usersgroups.permissions";
    private static final String PERMISSIONS_DELETE_BULK_API = "usersgroups.delete-bulk-permission";
    private static final String DESCRIPTION_DETAILS = "Permission for DEPUTY_DISTRICT_JUDGE";

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Requester requester;

    @ServiceComponent(EVENT_PROCESSOR)
    @Inject
    private Sender sender;

    @Inject
    private FeatureControlGuard featureControlGuard;

    @Override
    public void execute(final DelegateExecution delegateExecution) {

        if (!featureControlGuard.isFeatureEnabled("camunda-remove-ddj-access")) {
            LOGGER.info("Feature 'camunda-remove-ddj-access' is not enabled, skipping Remove DDJ Access Service Task.");
            return;
        }

        final Map<String, Object> delegateExecutionVariables = delegateExecution.getVariables();

        final String caseId = delegateExecutionVariables.get(CASE_ID).toString();

        LOGGER.info("Remove DDJ Access from case Service task triggered caseId {}", caseId);

        final JsonObject criteriaBuilder = createObjectBuilder()
                .add(TARGET, caseId)
                .add(ACTION, "Access")
                .add(OBJECT, "Case")
                .build();

        final JsonEnvelope queryEnvelope = envelopeFrom(metadataBuilder()
                .withId(randomUUID())
                .withName(PERMISSIONS_QUERY_API), criteriaBuilder);

        final Envelope<JsonObject> queryApiResponse = requester.requestAsAdmin(queryEnvelope, JsonObject.class);

        if (queryApiResponse != null && queryApiResponse.payload() != null) {
            final List<String> casePermissions = getDDJCasePermissions(queryApiResponse.payload(), caseId);

            if (!casePermissions.isEmpty()) {
                final Metadata metadata = metadataBuilder()
                        .withId(randomUUID())
                        .withName(PERMISSIONS_DELETE_BULK_API)
                        .build();

                final JsonArrayBuilder arrayBuilder = createArrayBuilder();

                casePermissions.stream().forEach(arrayBuilder::add);

                final JsonObject deletePermissionsPayload = createObjectBuilder()
                        .add("permissionIds", arrayBuilder.build()).build();

                sender.sendAsAdmin(envelopeFrom(metadata, deletePermissionsPayload));
            } else {
                LOGGER.info("Unable to remove DDJ Access, No DDJ permissions associated with case: {}", caseId);
            }
        }
    }

    private List<String> getDDJCasePermissions(final JsonObject jsonObject, final String caseId) {
        final JsonArray jsonArray = jsonObject.getJsonArray(PERMISSIONS);

        LOGGER.info("Found {} Access Case permissions for caseId: {} size {}", jsonArray, caseId, jsonArray.size());

        return IntStream.range(0, jsonArray.size())
                .mapToObj(jsonArray::getJsonObject)
                .filter(permissionObject -> permissionObject.containsKey(DESCRIPTION) && permissionObject.getString(DESCRIPTION).contains(DESCRIPTION_DETAILS))
                .map(t -> t.getString(PERMISSION_ID))
                .collect(Collectors.toList());
    }
}
