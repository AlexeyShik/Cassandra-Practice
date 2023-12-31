/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.cassandra.schema;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.ObjectUtils;

import org.apache.cassandra.Util;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.BufferDecoratedKey;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DeletionTime;
import org.apache.cassandra.db.Directories;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.SerializationHeader;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.db.memtable.Memtable;
import org.apache.cassandra.db.memtable.SkipListMemtable;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.apache.cassandra.io.sstable.Component;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.SSTableId;
import org.apache.cassandra.io.sstable.format.SSTableFormat;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.sstable.format.big.BigFormat;
import org.apache.cassandra.io.sstable.format.big.BigFormat.Components;
import org.apache.cassandra.io.sstable.format.big.BigTableReader;
import org.apache.cassandra.io.sstable.format.bti.BtiFormat;
import org.apache.cassandra.io.sstable.format.bti.BtiTableReader;
import org.apache.cassandra.io.sstable.format.bti.PartitionIndex;
import org.apache.cassandra.io.sstable.indexsummary.IndexSummary;
import org.apache.cassandra.io.sstable.keycache.KeyCache;
import org.apache.cassandra.io.sstable.metadata.MetadataCollector;
import org.apache.cassandra.io.sstable.metadata.MetadataType;
import org.apache.cassandra.io.sstable.metadata.StatsMetadata;
import org.apache.cassandra.io.util.DataOutputStreamPlus;
import org.apache.cassandra.io.util.File;
import org.apache.cassandra.io.util.FileHandle;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.io.util.Memory;
import org.apache.cassandra.service.CacheService;
import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FilterFactory;
import org.apache.cassandra.utils.Throwables;

import static org.apache.cassandra.service.ActiveRepairService.UNREPAIRED_SSTABLE;

public class MockSchema
{
    public static Supplier<? extends SSTableId> sstableIdGenerator = Util.newSeqGen();

    public static final ConcurrentMap<Integer, SSTableId> sstableIds = new ConcurrentHashMap<>();

    public static SSTableId sstableId(int idx)
    {
        return sstableIds.computeIfAbsent(idx, ignored -> sstableIdGenerator.get());
    }

    private static final File tempFile = temp("mocksegmentedfile");

    static
    {
        Memory offsets = Memory.allocate(4);
        offsets.setInt(0, 0);
        indexSummary = new IndexSummary(Murmur3Partitioner.instance, offsets, 0, Memory.allocate(4), 0, 0, 0, 1);

        try (DataOutputStreamPlus out = tempFile.newOutputStream(File.WriteMode.OVERWRITE))
        {
            out.write(new byte[10]);
        }
        catch (IOException ex)
        {
            throw Throwables.throwAsUncheckedException(ex);
        }

        Schema.instance = new MockSchemaProvider();
        if (DatabaseDescriptor.isDaemonInitialized() || DatabaseDescriptor.isToolInitialized())
            DatabaseDescriptor.createAllDirectories();

    }

    private static final AtomicInteger id = new AtomicInteger();
    private static final String ksname = "mockks";
    private static KeyspaceMetadata mockKS = KeyspaceMetadata.create(ksname, KeyspaceParams.simpleTransient(1));

    public static final IndexSummary indexSummary;

    public static Memtable memtable(ColumnFamilyStore cfs)
    {
        return SkipListMemtable.FACTORY.create(null, cfs.metadata, cfs);
    }

    public static SSTableReader sstable(int generation, ColumnFamilyStore cfs)
    {
        return sstable(generation, false, cfs);
    }

    public static SSTableReader sstable(int generation, long first, long last, ColumnFamilyStore cfs)
    {
        return sstable(generation, 0, false, first, last, cfs);
    }

    public static SSTableReader sstable(int generation, long first, long last, int minLocalDeletionTime, ColumnFamilyStore cfs)
    {
        return sstable(generation, 0, false, first, last, 0, cfs, minLocalDeletionTime);
    }

    public static SSTableReader sstable(int generation, boolean keepRef, ColumnFamilyStore cfs)
    {
        return sstable(generation, 0, keepRef, cfs);
    }

    public static SSTableReader sstable(int generation, int size, ColumnFamilyStore cfs)
    {
        return sstable(generation, size, false, cfs);
    }

    public static SSTableReader sstable(int generation, int size, boolean keepRef, ColumnFamilyStore cfs)
    {
        return sstable(generation, size, keepRef, generation, generation, cfs);
    }

    public static SSTableReader sstableWithLevel(int generation, long firstToken, long lastToken, int level, ColumnFamilyStore cfs)
    {
        return sstable(generation, 0, false, firstToken, lastToken, level, cfs);
    }

    public static SSTableReader sstableWithLevel(int generation, int size, int level, ColumnFamilyStore cfs)
    {
        return sstable(generation, size, false, generation, generation, level, cfs);
    }

    public static SSTableReader sstableWithTimestamp(int generation, long timestamp, ColumnFamilyStore cfs)
    {
        return sstable(generation, 0, false, 0, 1000, 0, cfs, Integer.MAX_VALUE, timestamp);
    }

    public static SSTableReader sstable(int generation, int size, boolean keepRef, long firstToken, long lastToken, int level, ColumnFamilyStore cfs)
    {
        return sstable(generation, size, keepRef, firstToken, lastToken, level, cfs, Integer.MAX_VALUE);
    }

    public static SSTableReader sstable(int generation, int size, boolean keepRef, long firstToken, long lastToken, ColumnFamilyStore cfs)
    {
        return sstable(generation, size, keepRef, firstToken, lastToken, 0, cfs);
    }

    public static SSTableReader sstable(int generation, int size, boolean keepRef, long firstToken, long lastToken, int level, ColumnFamilyStore cfs, int minLocalDeletionTime)
    {
        return sstable(generation, size, keepRef, firstToken, lastToken, level, cfs, minLocalDeletionTime, System.currentTimeMillis() * 1000);
    }

    public static SSTableReader sstable(int generation, int size, boolean keepRef, long firstToken, long lastToken, int level, ColumnFamilyStore cfs, int minLocalDeletionTime, long timestamp)
    {
        SSTableFormat<?, ?> format = DatabaseDescriptor.getSelectedSSTableFormat();
        Descriptor descriptor = new Descriptor(cfs.getDirectories().getDirectoryForNewSSTables(),
                                               cfs.getKeyspaceName(),
                                               cfs.getTableName(),
                                               sstableId(generation),
                                               format);

        if (BigFormat.is(format))
        {
            Set<Component> components = ImmutableSet.of(Components.DATA, Components.PRIMARY_INDEX, Components.FILTER, Components.TOC);
            for (Component component : components)
            {
                File file = descriptor.fileFor(component);
                file.createFileIfNotExists();
            }
            // .complete() with size to make sstable.onDiskLength work
            try (FileHandle fileHandle = new FileHandle.Builder(tempFile).bufferSize(size).withLengthOverride(size).complete())
            {
                maybeSetDataLength(descriptor, size);
                SerializationHeader header = SerializationHeader.make(cfs.metadata(), Collections.emptyList());
                MetadataCollector collector = new MetadataCollector(cfs.metadata().comparator);
                collector.update(DeletionTime.build(timestamp, minLocalDeletionTime));
                BufferDecoratedKey first = readerBounds(firstToken);
                BufferDecoratedKey last = readerBounds(lastToken);
                StatsMetadata metadata =
                                       (StatsMetadata) collector.sstableLevel(level)
                                                                .finalizeMetadata(cfs.metadata().partitioner.getClass().getCanonicalName(),
                                                                                  0.01f,
                                                                                  UNREPAIRED_SSTABLE,
                                                                                  null,
                                                                                  false,
                                                                                  header,
                                                                                  first.retainable().getKey().slice(),
                                                                                  last.retainable().getKey().slice())
                                                                .get(MetadataType.STATS);
                BigTableReader reader = new BigTableReader.Builder(descriptor).setComponents(components)
                                                                              .setTableMetadataRef(cfs.metadata)
                                                                              .setDataFile(fileHandle.sharedCopy())
                                                                              .setIndexFile(fileHandle.sharedCopy())
                                                                              .setIndexSummary(indexSummary.sharedCopy())
                                                                              .setFilter(FilterFactory.AlwaysPresent)
                                                                              .setMaxDataAge(1L)
                                                                              .setStatsMetadata(metadata)
                                                                              .setOpenReason(SSTableReader.OpenReason.NORMAL)
                                                                              .setSerializationHeader(header)
                                                                              .setFirst(first)
                                                                              .setLast(last)
                                                                              .setKeyCache(cfs.metadata().params.caching.cacheKeys ? new KeyCache(CacheService.instance.keyCache)
                                                                                                                                   : KeyCache.NO_CACHE)
                                                                              .build(cfs, false, false);
                if (!keepRef)
                    reader.selfRef().release();
                return reader;
            }
        }
        else if (BtiFormat.is(format))
        {
            Set<Component> components = ImmutableSet.of(Components.DATA, BtiFormat.Components.PARTITION_INDEX, BtiFormat.Components.ROW_INDEX, Components.FILTER, Components.TOC);
            for (Component component : components)
            {
                File file = descriptor.fileFor(component);
                file.createFileIfNotExists();
            }
            // .complete() with size to make sstable.onDiskLength work
            try (FileHandle fileHandle = new FileHandle.Builder(tempFile).bufferSize(size).withLengthOverride(size).complete())
            {
                maybeSetDataLength(descriptor, size);
                SerializationHeader header = SerializationHeader.make(cfs.metadata(), Collections.emptyList());
                MetadataCollector collector = new MetadataCollector(cfs.metadata().comparator);
                collector.update(DeletionTime.build(timestamp, minLocalDeletionTime));
                BufferDecoratedKey first = readerBounds(firstToken);
                BufferDecoratedKey last = readerBounds(lastToken);
                StatsMetadata metadata = (StatsMetadata) collector.sstableLevel(level)
                                                                  .finalizeMetadata(cfs.metadata().partitioner.getClass().getCanonicalName(), 0.01f, UNREPAIRED_SSTABLE, null, false, header, first.retainable().getKey(), last.retainable().getKey())
                                                                  .get(MetadataType.STATS);
                BtiTableReader reader = new BtiTableReader.Builder(descriptor).setComponents(components)
                                                                              .setTableMetadataRef(cfs.metadata)
                                                                              .setDataFile(fileHandle.sharedCopy())
                                                                              .setPartitionIndex(new PartitionIndex(fileHandle.sharedCopy(), 0, 0, readerBounds(firstToken), readerBounds(lastToken)))
                                                                              .setRowIndexFile(fileHandle.sharedCopy())
                                                                              .setFilter(FilterFactory.AlwaysPresent)
                                                                              .setMaxDataAge(1L)
                                                                              .setStatsMetadata(metadata)
                                                                              .setOpenReason(SSTableReader.OpenReason.NORMAL)
                                                                              .setSerializationHeader(header)
                                                                              .setFirst(readerBounds(firstToken))
                                                                              .setLast(readerBounds(lastToken))
                                                                              .build(cfs, false, false);
                if (!keepRef)
                    reader.selfRef().release();
                return reader;
            }
        }
        else
        {
            throw Util.testMustBeImplementedForSSTableFormat();
        }
    }

    private static void maybeSetDataLength(Descriptor descriptor, long size)
    {
        if (size > 0)
        {
            try
            {
                File file = descriptor.fileFor(Components.DATA);
                Util.setFileLength(file, size);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public static ColumnFamilyStore newCFS()
    {
        return newCFS(ksname);
    }

    public static ColumnFamilyStore newCFS(String ksname)
    {
        return newCFS(newTableMetadata(ksname));
    }

    public static ColumnFamilyStore newCFS(Function<TableMetadata.Builder, TableMetadata.Builder> options)
    {
        return newCFS(ksname, options);
    }

    public static ColumnFamilyStore newCFS(String ksname, Function<TableMetadata.Builder, TableMetadata.Builder> options)
    {
        return newCFS(options.apply(newTableMetadataBuilder(ksname)).build());
    }

    public static ColumnFamilyStore newCFS(TableMetadata metadata)
    {
        Tables tables = mockKS.tables.getNullable(metadata.name) == null
                        ? mockKS.tables.with(metadata)
                        : mockKS.tables.withSwapped(metadata);
        mockKS = mockKS.withSwapped(tables);
        return new ColumnFamilyStore(new Keyspace(mockKS), metadata.name, Util.newSeqGen(), metadata, new Directories(metadata), false, false);
    }

    private static TableMetadata newTableMetadata(String ksname)
    {
        return newTableMetadata(ksname, "mockcf" + (id.incrementAndGet()));
    }

    public static TableMetadata newTableMetadata(String ksname, String cfname)
    {
        return newTableMetadataBuilder(ksname, cfname).build();
    }

    public static TableMetadata.Builder newTableMetadataBuilder(String ksname)
    {
        return newTableMetadataBuilder(ksname, "mockcf" + (id.incrementAndGet()));
    }

    public static TableMetadata.Builder newTableMetadataBuilder(String ksname, String cfname)
    {
        return TableMetadata.builder(ksname, cfname)
                            .partitioner(Murmur3Partitioner.instance)
                            .addPartitionKeyColumn("key", UTF8Type.instance)
                            .addClusteringColumn("col", UTF8Type.instance)
                            .addRegularColumn("value", UTF8Type.instance)
                            .caching(CachingParams.CACHE_NOTHING);
    }

    public static BufferDecoratedKey readerBounds(long generation)
    {
        return new BufferDecoratedKey(new Murmur3Partitioner.LongToken(generation), ByteBufferUtil.EMPTY_BYTE_BUFFER);
    }

    private static File temp(String id)
    {
        File file = FileUtils.createTempFile(id, "tmp");
        file.deleteOnExit();
        return file;
    }

    public static void cleanup()
    {
        // clean up data directory which are stored as data directory/keyspace/data files
        for (String dirName : DatabaseDescriptor.getAllDataFileLocations())
        {
            File dir = new File(dirName);
            if (!dir.exists())
                continue;
            String[] children = dir.tryListNames();
            for (String child : children)
                FileUtils.deleteRecursive(new File(dir, child));
        }
    }

    public static class MockSchemaProvider implements SchemaProvider
    {
        private final SchemaProvider originalSchemaProvider = Schema.instance;

        @Override
        public Set<String> getKeyspaces()
        {
            Set<String> kss = new HashSet<>(originalSchemaProvider.getKeyspaces());
            kss.add(ksname);
            return kss;
        }

        @Override
        public int getNumberOfTables()
        {
            return originalSchemaProvider.getNumberOfTables() + mockKS.tables.size();
        }

        @Override
        public ClusterMetadata submit(SchemaTransformation transformation)
        {
            return originalSchemaProvider.submit(transformation);
        }

        @Override
        public Keyspaces localKeyspaces()
        {
            return originalSchemaProvider.localKeyspaces();
        }

        @Override
        public Keyspaces distributedKeyspaces()
        {
            return originalSchemaProvider.distributedKeyspaces().with(mockKS);
        }

        @Override
        public Keyspaces distributedAndLocalKeyspaces()
        {
            return Keyspaces.NONE.with(localKeyspaces()).with(distributedKeyspaces());
        }

        @Override
        public Keyspaces getUserKeyspaces()
        {
            return distributedKeyspaces().without(SchemaConstants.REPLICATED_SYSTEM_KEYSPACE_NAMES);
        }

        @Override
        public void registerListener(SchemaChangeListener listener)
        {
            originalSchemaProvider.registerListener(listener);
        }

        @Override
        public void unregisterListener(SchemaChangeListener listener)
        {
            originalSchemaProvider.unregisterListener(listener);
        }

        public SchemaChangeNotifier schemaChangeNotifier()
        {
            return originalSchemaProvider.schemaChangeNotifier();
        }

        @Override
        public Optional<TableMetadata> getIndexMetadata(String keyspace, String index)
        {
            return originalSchemaProvider.getIndexMetadata(keyspace, index);
        }

        @Override
        public Iterable<TableMetadata> getTablesAndViews(String keyspaceName)
        {
            Preconditions.checkNotNull(keyspaceName);
            KeyspaceMetadata ksm = ObjectUtils.getFirstNonNull(() -> distributedKeyspaces().getNullable(keyspaceName),
                                                               () -> localKeyspaces().getNullable(keyspaceName));
            Preconditions.checkNotNull(ksm, "Keyspace %s not found", keyspaceName);
            return ksm.tablesAndViews();
        }

        @Nullable
        @Override
        public Keyspace getKeyspaceInstance(String keyspaceName)
        {
            if (isMockKS(keyspaceName))
                return new Keyspace(mockKS);

            return originalSchemaProvider.getKeyspaceInstance(keyspaceName);
        }

        @Nullable
        @Override
        public KeyspaceMetadata getKeyspaceMetadata(String keyspaceName)
        {
            if (isMockKS(keyspaceName))
                return mockKS;
            return originalSchemaProvider.getKeyspaceMetadata(keyspaceName);
        }

        @Nullable
        @Override
        public TableMetadata getTableMetadata(TableId id)
        {
            if (mockKS.tables.containsTable(id))
                return mockKS.tables.getNullable(id);
            return originalSchemaProvider.getTableMetadata(id);
        }

        @Nullable
        @Override
        public TableMetadata getTableMetadata(String keyspace, String table)
        {
            if (isMockKS(keyspace) || mockKS.tables.stream().anyMatch(tm -> tm.name.equals(table)))
                return mockKS.tables.getNullable(table);
            return originalSchemaProvider.getTableMetadata(keyspace, table);
        }

        @Override
        public void saveSystemKeyspace()
        {
            originalSchemaProvider.saveSystemKeyspace();
        }

        private boolean isMockKS(String keyspaceName)
        {
            return keyspaceName.equals(ksname)|| keyspaceName.equals(mockKS.name) || mockKS.tables.stream().anyMatch(tm -> tm.keyspace.equals(keyspaceName));
        }
    }
}
