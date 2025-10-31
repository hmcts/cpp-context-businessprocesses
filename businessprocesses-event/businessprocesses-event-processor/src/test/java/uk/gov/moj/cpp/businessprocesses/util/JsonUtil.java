package uk.gov.moj.cpp.businessprocesses.util;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.json.JsonObject;

import com.google.common.io.Resources;

public class JsonUtil {

    public static final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    public static JsonObject getJsonObjectFromResource(final String path) throws IOException {
        final String response = Resources.toString(
                Resources.getResource(path),
                Charset.defaultCharset()
        );

        return stringToJsonObjectConverter.convert(response);
    }

}
