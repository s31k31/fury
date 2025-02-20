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

package io.fury;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.fury.serializer.CompatibleMode;
import io.fury.test.bean.Cyclic;
import io.fury.test.bean.FinalCyclic;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CyclicTest extends FuryTestBase {

  public static Object[][] beans() {
    return new Object[][] {
      {Cyclic.create(false), Cyclic.create(true)},
      {FinalCyclic.create(false), FinalCyclic.create(true)}
    };
  }

  @DataProvider
  public static Object[][] fury() {
    return Sets.cartesianProduct(
            ImmutableSet.of(true, false), // enableCodegen
            ImmutableSet.of(true, false), // async compilation
            ImmutableSet.of(
                CompatibleMode.SCHEMA_CONSISTENT, CompatibleMode.COMPATIBLE) // structFieldsRepeat
            )
        .stream()
        .map(List::toArray)
        .map(
            c ->
                new Object[] {
                  Fury.builder()
                      .withLanguage(Language.JAVA)
                      .withCodegen((Boolean) c[0])
                      .withAsyncCompilationEnabled((Boolean) c[1])
                      .withCompatibleMode((CompatibleMode) c[2])
                      .requireClassRegistration(false)
                })
        .toArray(Object[][]::new);
  }

  @Test(dataProvider = "fury")
  public void testBean(Fury.FuryBuilder builder) {
    Fury fury = builder.withMetaContextShareEnabled(false).withRefTracking(true).build();
    for (Object[] objects : beans()) {
      Object notCyclic = objects[0];
      Object cyclic = objects[1];
      Assert.assertEquals(notCyclic, fury.deserialize(fury.serialize(notCyclic)));
      Assert.assertEquals(cyclic, fury.deserialize(fury.serialize(cyclic)));
      Object[] arr = new Object[2];
      arr[0] = arr;
      arr[1] = cyclic;
      Assert.assertEquals(arr[1], ((Object[]) fury.deserialize(fury.serialize(arr)))[1]);
      List<Object> list = new ArrayList<>();
      list.add(list);
      list.add(cyclic);
      list.add(arr);
      Assert.assertEquals(
          ((Object[]) list.get(2))[1],
          ((Object[]) ((List) fury.deserialize(fury.serialize(list))).get(2))[1]);
    }
  }

  @Test(dataProvider = "fury")
  public void testBeanMetaShared(Fury.FuryBuilder builder) {
    Fury fury = builder.withMetaContextShareEnabled(true).withRefTracking(true).build();
    for (Object[] objects : beans()) {
      Object notCyclic = objects[0];
      Object cyclic = objects[1];
      Assert.assertEquals(notCyclic, serDeMetaShared(fury, notCyclic));
      Assert.assertEquals(cyclic, serDeMetaShared(fury, cyclic));
      Object[] arr = new Object[2];
      arr[0] = arr;
      arr[1] = cyclic;
      Assert.assertEquals(arr[1], ((Object[]) serDeMetaShared(fury, arr))[1]);
      List<Object> list = new ArrayList<>();
      list.add(list);
      list.add(cyclic);
      list.add(arr);
      Assert.assertEquals(
          ((Object[]) list.get(2))[1], ((Object[]) ((List) serDeMetaShared(fury, list)).get(2))[1]);
    }
  }
}
