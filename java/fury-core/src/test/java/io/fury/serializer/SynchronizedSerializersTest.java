/*
 * Copyright 2023 The Fury authors
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

package io.fury.serializer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import io.fury.Fury;
import io.fury.FuryTestBase;
import io.fury.Language;
import io.fury.memory.MemoryBuffer;
import io.fury.memory.MemoryUtils;
import io.fury.test.bean.CollectionFields;
import io.fury.util.Platform;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.testng.annotations.Test;

public class SynchronizedSerializersTest extends FuryTestBase {
  @Test
  public void testWrite() throws Exception {
    Fury fury = Fury.builder().withLanguage(Language.JAVA).requireClassRegistration(false).build();
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    Object[] values =
        new Object[] {
          Collections.synchronizedCollection(Collections.singletonList("abc")),
          Collections.synchronizedCollection(Arrays.asList(1, 2)),
          Collections.synchronizedList(Arrays.asList("abc", "def")),
          Collections.synchronizedList(new LinkedList<>(Arrays.asList("abc", "def"))),
          Collections.synchronizedSet(new HashSet<>(Arrays.asList("abc", "def"))),
          Collections.synchronizedSortedSet(new TreeSet<>(Arrays.asList("abc", "def"))),
          Collections.synchronizedMap(ImmutableMap.of("k1", "v1")),
          Collections.synchronizedSortedMap(new TreeMap<>(ImmutableMap.of("k1", "v1")))
        };
    for (Object value : values) {
      buffer.writerIndex(0);
      buffer.readerIndex(0);
      Serializer serializer;
      if (value instanceof Collection) {
        serializer =
            new SynchronizedSerializers.SynchronizedCollectionSerializer(
                fury,
                value.getClass(),
                SynchronizedSerializers.SynchronizedFactory.valueOfType(value.getClass()));
      } else {
        serializer =
            new SynchronizedSerializers.SynchronizedMapSerializer(
                fury,
                value.getClass(),
                SynchronizedSerializers.SynchronizedFactory.valueOfType(value.getClass()));
      }
      serializer.write(buffer, value);
      Object newObj = serializer.read(buffer);
      assertEquals(newObj.getClass(), value.getClass());
      SynchronizedSerializers.SynchronizedFactory synchronizedFactory =
          SynchronizedSerializers.SynchronizedFactory.valueOfType(newObj.getClass());
      Field field =
          SynchronizedSerializers.SynchronizedFactory.class.getDeclaredField("sourceFieldOffset");
      field.setAccessible(true);
      long sourceCollectionFieldOffset = (long) field.get(synchronizedFactory);
      Object innerValue = Platform.getObject(value, sourceCollectionFieldOffset);
      Object newValue = Platform.getObject(newObj, sourceCollectionFieldOffset);
      assertEquals(innerValue, newValue);
      newObj = serDe(fury, value);
      innerValue = Platform.getObject(value, sourceCollectionFieldOffset);
      newValue = Platform.getObject(newObj, sourceCollectionFieldOffset);
      assertEquals(innerValue, newValue);
      assertTrue(
          fury.getClassResolver()
              .getSerializerClass(value.getClass())
              .getName()
              .contains("Synchronized"));
    }
  }

  @Test(dataProvider = "javaFury")
  public void testCollectionFieldSerializers(Fury fury) {
    CollectionFields obj = new CollectionFields();
    Collection<Integer> collection = Collections.synchronizedCollection(Arrays.asList(1, 2));
    obj.collection = collection;
    obj.collection2 = Arrays.asList(1, 2);
    List<String> randomAccessList = Collections.synchronizedList(Arrays.asList("abc", "def"));
    obj.randomAccessList = randomAccessList;
    obj.randomAccessList2 = randomAccessList;
    obj.randomAccessList3 = randomAccessList;
    List<String> list = Collections.synchronizedList(new LinkedList<>(Arrays.asList("abc", "def")));
    obj.list = list;
    obj.list2 = list;
    obj.list3 = list;
    Set<String> set = Collections.synchronizedSet(new HashSet<>(Arrays.asList("abc", "def")));
    obj.set = set;
    obj.set2 = set;
    obj.set3 = set;
    SortedSet<String> treeSet =
        Collections.synchronizedSortedSet(new TreeSet<>(Arrays.asList("abc", "def")));
    obj.sortedSet = treeSet;
    obj.sortedSet2 = treeSet;
    obj.sortedSet3 = treeSet;
    Map<String, String> map = Collections.synchronizedMap(ImmutableMap.of("k1", "v1"));
    obj.map = map;
    obj.map2 = map;
    SortedMap<Integer, Integer> sortedMap =
        Collections.synchronizedSortedMap(new TreeMap<>(ImmutableMap.of(1, 2)));
    obj.sortedMap = sortedMap;
    obj.sortedMap2 = sortedMap;
    Object newObj = serDe(fury, obj);
    assertEquals(((CollectionFields) (newObj)).toCanEqual(), obj.toCanEqual());
  }
}
