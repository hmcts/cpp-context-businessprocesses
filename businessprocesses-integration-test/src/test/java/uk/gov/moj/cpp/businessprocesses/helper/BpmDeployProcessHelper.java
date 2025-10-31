package uk.gov.moj.cpp.businessprocesses.helper;

import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.apache.http.impl.client.HttpClients.createDefault;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmDeployProcessHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(BpmDeployProcessHelper.class);

    public static void deployProcessDefinition(final String filename, final String url) throws IOException {

        try (final CloseableHttpClient httpClient = createDefault()) {
            final HttpPost httpPost = new HttpPost(url);

            final StringBody deploymentName = new StringBody(filename, TEXT_PLAIN);
            final StringBody enableDuplicateFiltering = new StringBody("true", TEXT_PLAIN);
            final StringBody deployChangedOnly = new StringBody("true", TEXT_PLAIN);

            final MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .addPart("deployment-name", deploymentName)
                    .addPart("enable-duplicate-filtering", enableDuplicateFiltering)
                    .addPart("deploy-changed-only", deployChangedOnly);

            final File resourceFile = new File("src/test/resources/" + filename);
            final FileBody fileBody = new FileBody(resourceFile);
            builder.addPart(resourceFile.getName(), fileBody);

            final HttpEntity httpEntity = builder.build();
            httpPost.setEntity(httpEntity);

            final CloseableHttpResponse response = httpClient.execute(httpPost);
            final BufferedReader reader =new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            reader.lines().forEach(LOGGER::info);
        }
    }
}
