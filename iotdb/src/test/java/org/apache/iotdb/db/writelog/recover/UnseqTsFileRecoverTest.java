/**
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.writelog.recover;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.iotdb.db.engine.filenodeV2.TsFileResourceV2;
import org.apache.iotdb.db.engine.querycontext.OverflowInsertFile;
import org.apache.iotdb.db.engine.version.VersionController;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.qp.physical.crud.InsertPlan;
import org.apache.iotdb.db.query.reader.unsequence.UnsequenceSeriesReader;
import org.apache.iotdb.db.query.reader.unsequence.DiskChunkReader;
import org.apache.iotdb.db.utils.TimeValuePair;
import org.apache.iotdb.db.writelog.manager.MultiFileLogNodeManager;
import org.apache.iotdb.db.writelog.node.WriteLogNode;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetaData;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.Chunk;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.controller.ChunkLoader;
import org.apache.iotdb.tsfile.read.controller.ChunkLoaderImpl;
import org.apache.iotdb.tsfile.read.controller.MetadataQuerier;
import org.apache.iotdb.tsfile.read.controller.MetadataQuerierByFileImpl;
import org.apache.iotdb.tsfile.read.reader.chunk.ChunkReader;
import org.apache.iotdb.tsfile.read.reader.chunk.ChunkReaderWithoutFilter;
import org.apache.iotdb.tsfile.write.TsFileWriter;
import org.apache.iotdb.tsfile.write.record.TSRecord;
import org.apache.iotdb.tsfile.write.record.datapoint.DataPoint;
import org.apache.iotdb.tsfile.write.schema.FileSchema;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnseqTsFileRecoverTest {
  private File tsF;
  private TsFileWriter writer;
  private WriteLogNode node;
  private String logNodePrefix = "testNode";
  private FileSchema schema;
  private TsFileResourceV2 resource;
  private VersionController versionController = new VersionController() {
    private int i;
    @Override
    public long nextVersion() {
      return ++i;
    }

    @Override
    public long currVersion() {
      return i;
    }
  };

  @Before
  public void setup() throws IOException, WriteProcessException {
    tsF = new File("temp", "test.ts");
    tsF.getParentFile().mkdirs();

    schema = new FileSchema();
    for (int i = 0; i < 10; i++) {
      schema.registerMeasurement(new MeasurementSchema("sensor" + i, TSDataType.INT64,
          TSEncoding.PLAIN));
    }
    writer = new TsFileWriter(tsF, schema);

    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        TSRecord tsRecord = new TSRecord(i, "device" + j);
        for (int k = 0; k < 10; k++) {
          tsRecord.addTuple(DataPoint.getDataPoint(TSDataType.INT64, "sensor" + k,
              String.valueOf(k)));
        }
        writer.write(tsRecord);
      }
    }
    writer.flushForTest();
    writer.getIOWriter().close();

    node = MultiFileLogNodeManager.getInstance().getNode(logNodePrefix + tsF.getName());
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        String[] measurements = new String[10];
        String[] values = new String[10];
        for (int k = 0; k < 10; k++) {
          measurements[k] = "sensor" + k;
          values[k] = String.valueOf(k + 10);
        }
        InsertPlan insertPlan = new InsertPlan("device" + j, i, measurements, values);
        node.write(insertPlan);
      }
      node.notifyStartFlush();
    }
    resource = new TsFileResourceV2(tsF);
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(tsF.getParentFile());
    node.delete();
  }

  @Test
  public void test() throws ProcessorException, IOException {
    TsFileRecoverPerformer performer = new TsFileRecoverPerformer(logNodePrefix, schema,
        versionController, resource, true);
    performer.recover();

    TsFileSequenceReader fileReader = new TsFileSequenceReader(tsF.getPath(), true);
    MetadataQuerier metadataQuerier = new MetadataQuerierByFileImpl(fileReader);
    ChunkLoader chunkLoader = new ChunkLoaderImpl(fileReader);

    Path path = new Path("device1", "sensor1");

    OverflowInsertFile overflowInsertFile = new OverflowInsertFile(tsF.getPath(),
        metadataQuerier.getChunkMetaDataList(path));

    UnsequenceSeriesReader unSeqMergeReader = new UnsequenceSeriesReader();
    int priorityValue = 1;

    for (ChunkMetaData chunkMetaData : overflowInsertFile.getChunkMetaDataList()) {
      Chunk chunk = chunkLoader.getChunk(chunkMetaData);
      ChunkReader chunkReader = new ChunkReaderWithoutFilter(chunk);

      unSeqMergeReader
          .addReaderWithPriority(new DiskChunkReader(chunkReader),
              priorityValue);
      priorityValue++;
    }
    for (int i = 0; i < 10; i++) {
      TimeValuePair timeValuePair = unSeqMergeReader.current();
      assertEquals(i, timeValuePair.getTimestamp());
      assertEquals(11, timeValuePair.getValue().getLong());
      unSeqMergeReader.next();

    }
    unSeqMergeReader.close();
    fileReader.close();
  }
}