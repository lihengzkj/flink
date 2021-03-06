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

package org.apache.flink.formats.avro.typeutils;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerConfigSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSerializationUtil;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.runtime.PojoSerializer;
import org.apache.flink.api.java.typeutils.runtime.PojoSerializer.PojoSerializerConfigSnapshot;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.formats.avro.generated.User;
import org.apache.flink.formats.avro.utils.TestDataGenerator;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This test ensures that state and state configuration created by Flink 1.3 Avro types
 * that used the PojoSerializer still works.
 *
 * <p><b>Important:</b> Since Avro itself broke class compatibility between 1.7.7 (used in Flink 1.3)
 * and 1.8.2 (used in Flink 1.4), the Avro by Pojo compatibility is broken through Avro already.
 * This test only tests that the Avro serializer change (switching from Pojo to Avro for Avro types)
 * works properly.
 *
 * <p>This test can be dropped once we drop backwards compatibility with Flink 1.3 snapshots.
 */
public class BackwardsCompatibleAvroSerializerTest {

	private static final String SNAPSHOT_RESOURCE = "flink-1.3-avro-type-serializer-snapshot";

	private static final String DATA_RESOURCE = "flink-1.3-avro-type-serialized-data";

	@SuppressWarnings("unused")
	private static final String SNAPSHOT_RESOURCE_WRITER = "/data/repositories/flink/flink-formats/flink-avro/src/test/resources/" + SNAPSHOT_RESOURCE;

	@SuppressWarnings("unused")
	private static final String DATA_RESOURCE_WRITER = "/data/repositories/flink/flink-formats/flink-avro/src/test/resources/" + DATA_RESOURCE;

	private static final long RANDOM_SEED = 143065108437678L;

	private static final int NUM_DATA_ENTRIES = 20;

	@Test
	public void testCompatibilityWithFlink_1_3() throws Exception {

		// retrieve the old config snapshot

		final TypeSerializer<User> serializer;
		final TypeSerializerConfigSnapshot configSnapshot;

		try (InputStream in = getClass().getClassLoader().getResourceAsStream(SNAPSHOT_RESOURCE)) {
			DataInputViewStreamWrapper inView = new DataInputViewStreamWrapper(in);

			List<Tuple2<TypeSerializer<?>, TypeSerializerConfigSnapshot>> deserialized =
					TypeSerializerSerializationUtil.readSerializersAndConfigsWithResilience(
							inView, getClass().getClassLoader());

			assertEquals(1, deserialized.size());

			@SuppressWarnings("unchecked")
			final TypeSerializer<User> typedSerializer = (TypeSerializer<User>) deserialized.get(0).f0;

			serializer = typedSerializer;
			configSnapshot = deserialized.get(0).f1;
		}

		assertNotNull(serializer);
		assertNotNull(configSnapshot);

		assertTrue(serializer instanceof PojoSerializer);
		assertTrue(configSnapshot instanceof PojoSerializerConfigSnapshot);

		// sanity check for the test: check that the test data works with the original serializer
		validateDeserialization(serializer);

		// sanity check for the test: check that a PoJoSerializer and the original serializer work together
		assertFalse(serializer.ensureCompatibility(configSnapshot).isRequiresMigration());

		final TypeSerializer<User> newSerializer = new AvroTypeInfo<>(User.class, true).createSerializer(new ExecutionConfig());
		assertFalse(newSerializer.ensureCompatibility(configSnapshot).isRequiresMigration());

		// deserialize the data and make sure this still works
		validateDeserialization(newSerializer);

		TypeSerializerConfigSnapshot nextSnapshot = newSerializer.snapshotConfiguration();
		final TypeSerializer<User> nextSerializer = new AvroTypeInfo<>(User.class, true).createSerializer(new ExecutionConfig());

		assertFalse(nextSerializer.ensureCompatibility(nextSnapshot).isRequiresMigration());

		// deserialize the data and make sure this still works
		validateDeserialization(nextSerializer);
	}

	private static void validateDeserialization(TypeSerializer<User> serializer) throws IOException {
		final Random rnd = new Random(RANDOM_SEED);

		try (InputStream in = BackwardsCompatibleAvroSerializerTest.class.getClassLoader()
				.getResourceAsStream(DATA_RESOURCE)) {

			final DataInputViewStreamWrapper inView = new DataInputViewStreamWrapper(in);

			for (int i = 0; i < NUM_DATA_ENTRIES; i++) {
				final User deserialized = serializer.deserialize(inView);

				// deterministically generate a reference record
				final User reference = TestDataGenerator.generateRandomUser(rnd);

				assertEquals(reference, deserialized);
			}
		}
	}

// run this code on a 1.3 (or earlier) branch to generate the test data
//	public static void main(String[] args) throws Exception {
//
//		AvroTypeInfo<User> typeInfo = new AvroTypeInfo<>(User.class);
//
//		TypeSerializer<User> serializer = typeInfo.createPojoSerializer(new ExecutionConfig());
//		TypeSerializerConfigSnapshot confSnapshot = serializer.snapshotConfiguration();
//
//		try (FileOutputStream fos = new FileOutputStream(SNAPSHOT_RESOURCE_WRITER)) {
//			DataOutputViewStreamWrapper out = new DataOutputViewStreamWrapper(fos);
//
//			TypeSerializerSerializationUtil.writeSerializersAndConfigsWithResilience(
//					out,
//					Collections.singletonList(
//							new Tuple2<>(serializer, confSnapshot)));
//		}
//
//		try (FileOutputStream fos = new FileOutputStream(DATA_RESOURCE_WRITER)) {
//			final DataOutputViewStreamWrapper out = new DataOutputViewStreamWrapper(fos);
//			final Random rnd = new Random(RANDOM_SEED);
//
//			for (int i = 0; i < NUM_DATA_ENTRIES; i++) {
//				serializer.serialize(TestDataGenerator.generateRandomUser(rnd), out);
//			}
//		}
//	}
}
