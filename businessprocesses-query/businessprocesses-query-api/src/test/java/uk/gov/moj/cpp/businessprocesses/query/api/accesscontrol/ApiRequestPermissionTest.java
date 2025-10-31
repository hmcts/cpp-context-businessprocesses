package uk.gov.moj.cpp.businessprocesses.query.api.accesscontrol;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.moj.cpp.businessprocesses.query.api.accesscontrol.PermissionProvider.getViewPermissionFoAllUsers;

import uk.gov.moj.cpp.accesscontrol.common.providers.UserAndGroupProvider;
import uk.gov.moj.cpp.accesscontrol.drools.Action;
import uk.gov.moj.cpp.accesscontrol.test.utils.BaseDroolsAccessControlTest;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class ApiRequestPermissionTest extends BaseDroolsAccessControlTest {
    private static Map<String, String[]> requestPermissionMap = new HashMap<>();

    static {
        try {
            requestPermissionMap.put("businessprocesses.query.task-history", getViewPermissionFoAllUsers());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public ApiRequestPermissionTest() {
        super("QUERY_API_SESSION");
    }

    @Mock
    private UserAndGroupProvider userAndGroupProvider;

    @Override
    protected Map<Class<?>, Object> getProviderMocks() {
        return ImmutableMap.<Class<?>, Object>builder().put(UserAndGroupProvider.class, userAndGroupProvider).build();
    }

    @Test
    public void shouldAllowAllUsersToViewRequest() {
        requestPermissionMap.forEach((actionName, permissions) -> {
            final Action action = createActionFor(actionName);
            assertSuccessfulOutcome(executeRulesWith(action));
            verifyNoMoreInteractions(userAndGroupProvider);
        });
    }
}
