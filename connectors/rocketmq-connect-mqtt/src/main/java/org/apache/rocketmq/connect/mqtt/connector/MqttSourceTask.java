/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.connect.mqtt.connector;

import com.alibaba.fastjson.JSON;
import io.openmessaging.KeyValue;
import io.openmessaging.connector.api.component.task.source.SourceTask;
import io.openmessaging.connector.api.data.ConnectRecord;
import io.openmessaging.connector.api.data.Field;
import io.openmessaging.connector.api.data.RecordOffset;
import io.openmessaging.connector.api.data.RecordPartition;
import io.openmessaging.connector.api.data.Schema;
import io.openmessaging.connector.api.data.SchemaBuilder;
import io.openmessaging.connector.api.data.Struct;
import io.openmessaging.internal.DefaultKeyValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.connect.mqtt.config.SourceConnectorConfig;
import org.apache.rocketmq.connect.mqtt.source.Replicator;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttSourceTask extends SourceTask {

    private static final Logger log = LoggerFactory.getLogger(MqttSourceTask.class);

    private Replicator replicator;

    private SourceConnectorConfig sourceConnectConfig;

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> res = new ArrayList<>();
        try {
            MqttMessage message = replicator.getQueue().poll(1000, TimeUnit.MILLISECONDS);
            if (message != null) {
                res.add(message2ConnectRecord(message));
            }
        } catch (Exception e) {
            log.error("mqtt task poll error, current config:" + JSON.toJSONString(sourceConnectConfig), e);
        }
        return res;
    }

    @Override
    public void start(KeyValue props) {
        try {
            this.sourceConnectConfig = new SourceConnectorConfig();
            this.sourceConnectConfig.load(props);
            this.replicator = new Replicator(sourceConnectConfig);
            this.replicator.start();
        } catch (Exception e) {
            log.error("mqtt task start failed.", e);
        }
    }

    @Override
    public void stop() {
        try {
            replicator.stop();
        } catch (Exception e) {
            log.error("mqtt task stop failed.", e);
        }
    }

    private ConnectRecord message2ConnectRecord(MqttMessage message) {
        Schema schema = SchemaBuilder.struct().name("mqtt").build();
        final List<Field> fields = buildFields();
        schema.setFields(fields);
        final ConnectRecord connectRecord = new ConnectRecord(buildRecordPartition(),
            buildRecordOffset(message),
            System.currentTimeMillis(),
            schema,
            buildPayLoad(message));
        connectRecord.setExtensions(buildExtendFiled());
        return connectRecord;
    }

    private RecordOffset buildRecordOffset(MqttMessage message) {
        Map<String, Long> offsetMap = new HashMap<>();
        offsetMap.put(SourceConnectorConfig.POSITION, 0l);
        RecordOffset recordOffset = new RecordOffset(offsetMap);
        return recordOffset;
    }

    private RecordPartition buildRecordPartition() {
        Map<String, String> partitionMap = new HashMap<>();
        partitionMap.put("partition", "defaultPartition");
        RecordPartition recordPartition = new RecordPartition(partitionMap);
        return recordPartition;
    }

    private List<Field> buildFields() {
        final Schema structSchema = SchemaBuilder.struct().build();
        List<Field> fields = new ArrayList<>();
        fields.add(new Field(0, SourceConnectorConfig.MESSAGE, structSchema));
        return fields;
    }

    private String buildPayLoad(MqttMessage message) {
        return new String(message.getPayload());

    }

    private KeyValue buildExtendFiled() {
        KeyValue keyValue = new DefaultKeyValue();
        return keyValue;
    }
}