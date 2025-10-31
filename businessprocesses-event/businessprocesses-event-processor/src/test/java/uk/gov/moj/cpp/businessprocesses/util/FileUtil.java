package uk.gov.moj.cpp.businessprocesses.util;


import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.Charset;
import java.util.Map;

import javax.json.JsonObject;

import com.google.common.io.Resources;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    public static String getPayload(String path) {
        String request = null;
        try {
            request = Resources.toString(
                    Resources.getResource(path),
                    Charset.defaultCharset()
            );
        } catch (Exception e) {
            LOGGER.error("Error consuming file from location {}", path, e);
            fail("Error consuming file from location " + path);
        }
        return request;
    }

    public static String getFileContent(final String path, final Map<String, Object> namedPlaceholders) {
        return new StrSubstitutor(namedPlaceholders).replace(getPayload(path));
    }

    public static JsonObject getFileContentAsJson(final String path, final Map<String, Object> namedPlaceholders) {
        return JsonHelper.getJsonObject(getFileContent(path, namedPlaceholders));
    }

    public static JsonObject getFileContentAsJson(final String path) {
        return JsonHelper.getJsonObject(getFileContent(path, emptyMap()));
    }
}

