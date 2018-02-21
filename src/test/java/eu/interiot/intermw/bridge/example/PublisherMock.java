/**
 * INTER-IoT. Interoperability of IoT Platforms.
 * INTER-IoT is a R&D project which has received funding from the European
 * Union’s Horizon 2020 research and innovation programme under grant
 * agreement No 687283.
 * <p>
 * Copyright (C) 2016-2018, by (Author's company of this file):
 * - XLAB d.o.o.
 * <p>
 * This code is licensed under the EPL license, available at the root
 * application directory.
 */
package eu.interiot.intermw.bridge.example;

import eu.interiot.intermw.comm.broker.Broker;
import eu.interiot.intermw.comm.broker.Publisher;
import eu.interiot.intermw.comm.broker.Queue;
import eu.interiot.intermw.comm.broker.Topic;
import eu.interiot.intermw.comm.broker.exceptions.BrokerException;

import java.util.LinkedList;
import java.util.List;

class PublisherMock<M> implements Publisher<M> {
    private java.util.Queue<M> publishedMessages = new LinkedList<>();

    @Override
    public void publish(M m) throws BrokerException {
        publishedMessages.add(m);
    }

    @Override
    public void init(Broker broker, List<Queue> queues, Class<Topic<M>> topicClass) throws BrokerException {

    }

    @Override
    public void init(Broker broker, List<Queue> queues, String exchangeName, Class<M> messageClass) throws BrokerException {

    }

    @Override
    public void init(Broker broker, Class<? extends Topic<M>> topicClass) throws BrokerException {

    }

    @Override
    public void init(Broker broker, String exchangeName, Class<M> messageClass) throws BrokerException {

    }

    @Override
    public void cleanUp() throws BrokerException {

    }

    @Override
    public Topic<M> getTopic() {
        return null;
    }

    @Override
    public void createTopic(String name) throws BrokerException {

    }

    @Override
    public void deleteTopic(String name) throws BrokerException {

    }

    public M retrieveMessage() {
        return publishedMessages.poll();
    }
}
