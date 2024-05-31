/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.connector.dynamodb.table;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.connector.base.sink.writer.ElementConverter;
import org.apache.flink.connector.dynamodb.sink.DynamoDbWriteRequest;
import org.apache.flink.connector.dynamodb.sink.DynamoDbWriteRequestType;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of an {@link ElementConverter} for the DynamoDb Table sink. The element converter
 * maps the Flink internal type of {@link RowData} to a {@link DynamoDbWriteRequest} to be used by
 * the DynamoDb sink.
 */
@Internal
public class RowDataElementConverter implements ElementConverter<RowData, DynamoDbWriteRequest> {

    private final DataType physicalDataType;
    private transient RowDataToAttributeValueConverter rowDataToAttributeValueConverter;

    public RowDataElementConverter(DataType physicalDataType) {
        this.physicalDataType = physicalDataType;
        this.rowDataToAttributeValueConverter =
                new RowDataToAttributeValueConverter(physicalDataType);
    }

    @Override
    public DynamoDbWriteRequest apply(RowData element, SinkWriter.Context context) {
        if (rowDataToAttributeValueConverter == null) {
            rowDataToAttributeValueConverter =
                    new RowDataToAttributeValueConverter(physicalDataType);
        }

        Map<String, AttributeValue> itemMap = rowDataToAttributeValueConverter.convertRowData(element);
        DynamoDbWriteRequest.Builder builder = DynamoDbWriteRequest.builder().setItem(itemMap);

        switch (element.getRowKind()) {
            case INSERT:
            case UPDATE_AFTER:
                builder.setItem(itemMap);
                builder.setType(DynamoDbWriteRequestType.PUT);
                break;
            case DELETE:
                /* For Delete Requests, we must only send the PK, and not all other fields */
                builder.setType(DynamoDbWriteRequestType.DELETE);
                Map<String, AttributeValue> pkOnlyMap = new HashMap<String, AttributeValue>();
                pkOnlyMap.put("userId", itemMap.get("userId"));
                builder.setItem(pkOnlyMap);
                break;
            case UPDATE_BEFORE:
            default:
                throw new TableException("Unsupported message kind: " + element.getRowKind());
        }

        return builder.build();
    }
}
