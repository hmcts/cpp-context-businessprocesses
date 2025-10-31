package uk.gov.moj.cpp.businessprocesses.query.api.accesscontrol;

import static uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission.builder;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PermissionProvider {
    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
    private static final String UPDATE = "Update";
    private static final String VIEW = "View";
    private static final String CTSC_MANAGER = "CTSC_Manager";
    private static final String OBJECT = "WorkManagement";
    private static final String ANY_ROLE = "AnyRole";

    private PermissionProvider() {
    }

    public static String[] getViewPermission() throws JsonProcessingException {
        final ExpectedPermission viewPermission = builder()
                .withAction(VIEW)
                .withObject(OBJECT)
                .build();
        return new String[]{objectMapper.writeValueAsString(viewPermission)};
    }

    public static String[] getUpdatePermission() throws JsonProcessingException {
        final ExpectedPermission updatePermission = builder()
                .withAction(UPDATE)
                .withObject(OBJECT)
                .build();
        return new String[]{objectMapper.writeValueAsString(updatePermission)};
    }

    public static String[] getCTSCManagerPermission() throws JsonProcessingException {
        final ExpectedPermission managerPermission = builder()
                .withAction(UPDATE)
                .withObject(CTSC_MANAGER)
                .build();
        return new String[]{objectMapper.writeValueAsString(managerPermission)};
    }

    public static String[] getViewPermissionFoAllUsers() throws JsonProcessingException {
        final ExpectedPermission viewPermission = builder()
                .withAction(VIEW)
                .withObject(ANY_ROLE)
                .build();
        return new String[]{objectMapper.writeValueAsString(viewPermission)};
    }
}
