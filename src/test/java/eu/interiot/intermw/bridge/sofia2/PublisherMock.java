/*
 * Copyright 2020 Universitat Politècnica de València
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.interiot.intermw.bridge.sofia2;

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
