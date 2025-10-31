package uk.gov.moj.cpp.businessprocesses.helper;

import static javax.jms.Session.AUTO_ACKNOWLEDGE;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.QueueUriProvider.queueUri;

import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.json.JsonObject;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public class CustomMessageProducerClient implements AutoCloseable {

    private static final String QUEUE_URI = queueUri();

    private Session session;
    private MessageProducer messageProducer;
    private Connection connection;

    public void startProducer(final String topicName) {

        //TODO this code is atrocious
        try {
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(QUEUE_URI);
            connection = factory.createConnection();
            connection.start();

            session = connection.createSession(false, AUTO_ACKNOWLEDGE);
            final Destination destination = session.createTopic(topicName);
            messageProducer = session.createProducer(destination);
        } catch (final JMSException e) {
            close();
            throw new RuntimeException("Failed to create message producer to topic: '" + topicName + "', queue uri: '" + QUEUE_URI + "'", e);
        }
    }

    public void sendMessage(String commandName, JsonObject payload, Metadata metadata) {
        final JsonEnvelope jsonEnvelope = envelopeFrom(metadata, payload);
        final String json = jsonEnvelope.toDebugStringPrettyPrint();

        try {
            final TextMessage message = session.createTextMessage();

            message.setText(json);
            message.setStringProperty("CPPNAME", commandName);

            messageProducer.send(message);
        } catch (JMSException e) {
            close();
            throw new RuntimeException("Failed to send message. commandName: '" + commandName + "', json: " + json, e);
        }
    }

    @Override
    public void close() {
        close(messageProducer);
        close(session);
        close(connection);

        session = null;
        messageProducer = null;
        connection = null;
    }

    private void close(final AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
