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

import io.fury.Fury;
import io.fury.io.ClassLoaderObjectInputStream;
import io.fury.io.FuryObjectInput;
import io.fury.io.FuryObjectOutput;
import io.fury.memory.MemoryBuffer;
import io.fury.resolver.ClassResolver;
import io.fury.util.LoggerFactory;
import io.fury.util.Platform;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

/**
 * Serializes objects using Java's built in serialization to be compatible with java serialization.
 * This is very inefficient and should be avoided if possible. User can call {@link
 * Fury#registerSerializer} to avoid this.
 *
 * <p>When a serializer not found and {@link ClassResolver#requireJavaSerialization(Class)} return
 * true, this serializer will be used.
 *
 * @author chaokunyang
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JavaSerializer extends Serializer {
  private static final Logger LOG = LoggerFactory.getLogger(JavaSerializer.class);
  private final FuryObjectInput objectInput;
  private final FuryObjectOutput objectOutput;

  public JavaSerializer(Fury fury, Class<?> cls) {
    super(fury, cls);
    // TODO(chgaokunyang) enable this check when ObjectSerializer is implemented.
    // Preconditions.checkArgument(ClassResolver.requireJavaSerialization(cls));
    if (cls != SerializedLambda.class) {
      LOG.warn(
          "{} use java built-in serialization, which is inefficient. "
              + "Please replace it with a {} or implements {}",
          cls,
          Serializer.class.getCanonicalName(),
          Externalizable.class.getCanonicalName());
    }
    objectInput = new FuryObjectInput(fury, null);
    objectOutput = new FuryObjectOutput(fury, null);
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {
    try {
      objectOutput.setBuffer(buffer);
      ObjectOutputStream objectOutputStream =
          (ObjectOutputStream) fury.getSerializationContext().get(objectOutput);
      if (objectOutputStream == null) {
        objectOutputStream = new ObjectOutputStream(objectOutput);
        fury.getSerializationContext().add(objectOutput, objectOutputStream);
      }
      objectOutputStream.writeObject(value);
      objectOutputStream.flush();
    } catch (IOException e) {
      Platform.throwException(e);
    }
  }

  @Override
  public Object read(MemoryBuffer buffer) {
    try {
      objectInput.setBuffer(buffer);
      ObjectInputStream objectInputStream =
          (ObjectInputStream) fury.getSerializationContext().get(objectInput);
      if (objectInputStream == null) {
        objectInputStream = new ClassLoaderObjectInputStream(fury.getClassLoader(), objectInput);
        fury.getSerializationContext().add(objectInput, objectInputStream);
      }
      return objectInputStream.readObject();
    } catch (IOException | ClassNotFoundException e) {
      Platform.throwException(e);
    }
    throw new IllegalStateException("unreachable code");
  }

  public static Method getWriteObjectMethod(Class<?> clz) {
    return getWriteObjectMethod(clz, true);
  }

  public static Method getWriteObjectMethod(Class<?> clz, boolean searchParent) {
    Method writeObject = getMethod(clz, "writeObject", searchParent);
    if (writeObject != null) {
      if (isWriteObjectMethod(writeObject)) {
        return writeObject;
      }
    }
    return null;
  }

  public static boolean isWriteObjectMethod(Method method) {
    return method.getParameterTypes().length == 1
        && method.getParameterTypes()[0] == ObjectOutputStream.class
        && method.getReturnType() == void.class
        && Modifier.isPrivate(method.getModifiers());
  }

  public static Method getReadObjectMethod(Class<?> clz) {
    return getReadObjectMethod(clz, true);
  }

  public static Method getReadObjectMethod(Class<?> clz, boolean searchParent) {
    Method readObject = getMethod(clz, "readObject", searchParent);
    if (readObject != null && isReadObjectMethod(readObject)) {
      return readObject;
    }
    return null;
  }

  public static boolean isReadObjectMethod(Method method) {
    return method.getParameterTypes().length == 1
        && method.getParameterTypes()[0] == ObjectInputStream.class
        && method.getReturnType() == void.class
        && Modifier.isPrivate(method.getModifiers());
  }

  public static Method getReadObjectNoData(Class<?> clz, boolean searchParent) {
    Method method = getMethod(clz, "readObjectNoData", searchParent);
    if (method != null
        && method.getParameterTypes().length == 0
        && method.getReturnType() == void.class
        && Modifier.isPrivate(method.getModifiers())) {
      return method;
    }
    return null;
  }

  public static Method getReadResolveMethod(Class<?> clz) {
    Method readResolve = getMethod(clz, "readResolve", true);
    if (readResolve != null) {
      if (readResolve.getParameterTypes().length == 0
          && readResolve.getReturnType() == Object.class) {
        return readResolve;
      }
    }
    return null;
  }

  public static Method getWriteReplaceMethod(Class<?> clz) {
    Method writeReplace = getMethod(clz, "writeReplace", true);
    if (writeReplace != null) {
      if (writeReplace.getParameterTypes().length == 0
          && writeReplace.getReturnType() == Object.class) {
        return writeReplace;
      }
    }
    return null;
  }

  private static Method getMethod(Class<?> clz, String methodName, boolean searchParent) {
    Class<?> cls = clz;
    do {
      for (Method method : cls.getDeclaredMethods()) {
        if (method.getName().equals(methodName)) {
          return method;
        }
      }
      cls = cls.getSuperclass();
    } while (cls != null && searchParent);
    return null;
  }

  /**
   * Return true if current binary is serialized by JDK {@link ObjectOutputStream}.
   *
   * @see #serializedByJDK(byte[], int)
   */
  public static boolean serializedByJDK(byte[] data) {
    return serializedByJDK(data, 0);
  }

  /**
   * Return true if current binary is serialized by JDK {@link ObjectOutputStream}.
   *
   * <p>Note that one can fake magic number {@link ObjectStreamConstants#STREAM_MAGIC}, please use
   * this method carefully in a trusted environment. And it's not a strict check, if this method
   * return true, the data may be not serialized by JDK if other framework generate same magic
   * number by accident. But if this method return false, the data are definitely not serialized by
   * JDK.
   */
  public static boolean serializedByJDK(byte[] data, int offset) {
    // JDK serialization use big endian byte order.
    short magicNumber = MemoryBuffer.getShortB(data, offset);
    return magicNumber == ObjectStreamConstants.STREAM_MAGIC;
  }

  /**
   * Return true if current binary is serialized by JDK {@link ObjectOutputStream}.
   *
   * @see #serializedByJDK(byte[], int)
   */
  public static boolean serializedByJDK(ByteBuffer buffer, int offset) {
    // (short) ((b[off + 1] & 0xFF) + (b[off] << 8));
    byte b1 = buffer.get(offset + 1);
    byte b0 = buffer.get(offset);
    short magicNumber = (short) ((b1 & 0xFF) + (b0 << 8));
    return magicNumber == ObjectStreamConstants.STREAM_MAGIC;
  }
}
