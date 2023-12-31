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

package org.apache.cassandra.tcm.serialization;

import java.io.IOException;

import org.apache.cassandra.io.util.DataInputPlus;
import org.apache.cassandra.io.util.DataOutputPlus;
import org.apache.cassandra.schema.Types;

/**
 * This can of course be used *by* a MetadataSerializer (e.g. by KeyspaceMetadata.Serialize at the top level)
 * but it cannot be used *as* a MetadataSerializer, as more than just the input bytes + version are required
 */
public interface UDTAwareMetadataSerializer<T>
{

    /**
     * Serialize the specified type into the specified DataOutputStream instance.
     *
     * @param t type that needs to be serialized
     * @param out DataOutput into which serialization needs to happen.
     * @throws IOException if serialization fails
     */
    public void serialize(T t, DataOutputPlus out, Version version) throws IOException;

    /**
     * Deserialize into the specified DataInputStream instance.
     * @param in DataInput from which deserialization needs to happen.
     * @param types collection of UDTs required to deserialize the instance
     * @param version protocol version
     * @return the type that was deserialized
     * @throws IOException if deserialization fails
     */
    public T deserialize(DataInputPlus in, Types types, Version version) throws IOException;


    /**
     * Calculate serialized size of object without actually serializing.
     * @param t object to calculate serialized size
     * @param version protocol version
     * @return serialized size of object t
     */
    public long serializedSize(T t, Version version);
}
