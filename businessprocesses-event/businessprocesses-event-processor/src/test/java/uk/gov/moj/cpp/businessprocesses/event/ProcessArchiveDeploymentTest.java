package uk.gov.moj.cpp.businessprocesses.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class ProcessArchiveDeploymentTest {

    @Test
    void shouldDeployEachPackagedProcessOnceAndReuseUnchangedDeployments() throws Exception {
        final Path descriptor = Paths.get(getClass().getResource("/META-INF/processes.xml").toURI());
        final Path resourceRoot = descriptor.getParent().getParent();
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final Document document = factory.newDocumentBuilder().parse(descriptor.toFile());
        final NodeList archives = document.getElementsByTagNameNS("*", "process-archive");
        final List<Path> resources;
        try (final Stream<Path> files = Files.walk(resourceRoot)) {
            resources = files.filter(path -> path.toString().endsWith(".bpmn"))
                    .sorted().collect(Collectors.toList());
        }
        assertTrue(archives.getLength() > 0, "The application must deploy its workflows");
        assertTrue(resources.size() > 0, "The test must exercise the packaged BPMN resources");

        final ProcessEngine engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setProcessEngineName("process-archives-" + UUID.randomUUID())
                .setJdbcUrl("jdbc:h2:mem:process-archives-" + UUID.randomUUID())
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setJobExecutorActivate(false)
                .setHistory(ProcessEngineConfiguration.HISTORY_NONE)
                .buildProcessEngine();
        try {
            final RepositoryService repository = engine.getRepositoryService();
            deployScanningArchives(repository, archives, resourceRoot, resources);
            assertEquals(resources.size(), repository.createProcessDefinitionQuery().count(),
                    "Archive scans must not deploy the complete workflow bundle repeatedly");
            for (final ProcessDefinition definition : repository.createProcessDefinitionQuery().list()) {
                assertEquals(1, definition.getVersion(), definition.getKey());
                assertNotNull(repository.createProcessDefinitionQuery()
                        .processDefinitionKey(definition.getKey()).latestVersion().singleResult());
            }

            // An unchanged application restart must reuse the same definitions.
            deployScanningArchives(repository, archives, resourceRoot, resources);
            assertEquals(resources.size(), repository.createProcessDefinitionQuery().count());
            assertEquals(1, repository.createDeploymentQuery().count());
        } finally {
            engine.close();
        }
    }

    private void deployScanningArchives(final RepositoryService repository, final NodeList archives,
                                        final Path root, final List<Path> resources) throws Exception {
        for (int i = 0; i < archives.getLength(); i++) {
            final Element archive = (Element) archives.item(i);
            final NodeList properties = archive.getElementsByTagNameNS("*", "property");
            boolean scansForDefinitions = false;
            for (int j = 0; j < properties.getLength(); j++) {
                final Element property = (Element) properties.item(j);
                if ("isScanForProcessDefinitions".equals(property.getAttribute("name"))) {
                    scansForDefinitions = Boolean.parseBoolean(property.getTextContent());
                }
            }
            assertTrue(scansForDefinitions, "The archive must include all packaged workflows");
            final DeploymentBuilder deployment = repository.createDeployment()
                    .name(archive.getAttribute("name")).source("process application")
                    .enableDuplicateFiltering(true);
            for (final Path resource : resources) {
                deployment.addClasspathResource(root.relativize(resource).toString());
            }
            deployment.deploy();
        }
    }
}
