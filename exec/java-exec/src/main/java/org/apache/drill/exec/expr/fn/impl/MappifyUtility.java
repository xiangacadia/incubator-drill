/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.expr.fn.impl;

import com.google.common.base.Charsets;

import org.apache.drill.common.exceptions.DrillRuntimeException;
import org.apache.drill.common.types.TypeProtos;
import org.apache.drill.exec.expr.holders.VarCharHolder;
import org.apache.drill.exec.vector.complex.MapUtility;
import org.apache.drill.exec.vector.complex.impl.SingleMapReaderImpl;
import org.apache.drill.exec.vector.complex.reader.FieldReader;
import org.apache.drill.exec.vector.complex.writer.BaseWriter;

import io.netty.buffer.DrillBuf;

import java.util.Iterator;

public class MappifyUtility {

  // Default names used in the map.
  public static final String fieldKey = "key";
  public static final String fieldValue = "value";

  public static void mappify(FieldReader reader, BaseWriter.ComplexWriter writer, DrillBuf buffer) {
    // Currently we expect single map as input
    if (!(reader instanceof SingleMapReaderImpl)) {
      throw new DrillRuntimeException("kvgen function only supports Simple maps as input");
    }
    BaseWriter.ListWriter listWriter = writer.rootAsList();
    listWriter.start();
    BaseWriter.MapWriter mapWriter = listWriter.map();

    // Iterate over the fields in the map
    Iterator<String> fieldIterator = reader.iterator();
    while (fieldIterator.hasNext()) {
      String str = fieldIterator.next();
      FieldReader fieldReader = reader.reader(str);

      // Skip the field if its null
      if (fieldReader.isSet() == false) {
        mapWriter.end();
        continue;
      }

      // Check if the value field is not repeated
      if (fieldReader.getType().getMode() == TypeProtos.DataMode.REPEATED) {
        throw new DrillRuntimeException("kvgen function does not support repeated type values");
      }

      // writing a new field, start a new map
      mapWriter.start();

      // write "key":"columnname" into the map
      VarCharHolder vh = new VarCharHolder();
      byte[] b = str.getBytes(Charsets.UTF_8);
      buffer.reallocIfNeeded(b.length);
      buffer.setBytes(0, b);
      vh.start = 0;
      vh.end = b.length;
      vh.buffer = buffer;
      mapWriter.varChar(fieldKey).write(vh);

      // Write the value to the map
      MapUtility.writeToMapFromReader(fieldReader, mapWriter, buffer);

      mapWriter.end();
    }
    listWriter.end();
  }
}

