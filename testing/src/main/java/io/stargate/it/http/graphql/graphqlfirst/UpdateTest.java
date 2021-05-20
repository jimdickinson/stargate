/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.it.http.graphql.graphqlfirst;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.jayway.jsonpath.JsonPath;
import io.stargate.it.driver.CqlSessionExtension;
import io.stargate.it.driver.TestKeyspace;
import io.stargate.it.http.RestUtils;
import io.stargate.it.storage.StargateConnectionInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CqlSessionExtension.class)
public class UpdateTest extends GraphqlFirstTestBase {

  private static CqlSession SESSION;
  private static GraphqlFirstClient CLIENT;
  private static String KEYSPACE;

  @BeforeAll
  public static void setup(
      StargateConnectionInfo cluster, @TestKeyspace CqlIdentifier keyspaceId, CqlSession session) {
    SESSION = session;
    CLIENT =
        new GraphqlFirstClient(
            cluster.seedAddress(), RestUtils.getAuthToken(cluster.seedAddress()));
    KEYSPACE = keyspaceId.asInternal();
    CLIENT.deploySchema(
        KEYSPACE,
        "type User @cql_input {\n"
            + "  pk: Int! @cql_column(partitionKey: true)\n"
            + "  cc1: Int! @cql_column(clusteringOrder: ASC)\n"
            + "  cc2: Int @cql_column(clusteringOrder: ASC)\n"
            + "  username: String\n"
            + "}\n"
            + "type Query { user(pk: Int!, cc1: Int!, cc2: Int!): User }\n"
            + "type Mutation {\n"
            + "  updateUser(user: UserInput!): Boolean @cql_update\n"
            + "  updateUserPartialPk(user: UserInput!): Boolean @cql_update\n"
            + "}");
  }

  @BeforeEach
  public void cleanupData() {
    SESSION.execute("truncate table \"User\"");
  }

  private static void insert(int pk, int cc1, int cc2) {
    SESSION.execute("INSERT INTO \"Foo\"(pk, cc1, cc2) VALUES (?, ?, ?)", pk, cc1, cc2);
  }

  private static List<Row> findAllUsers(int pk, int cc1) {
    return SESSION.execute("SELECT * FROM \"User\" WHERE pk = ? AND cc1 = ?", pk, cc1).all();
  }

  @Test
  @DisplayName("Should update user with all PKs and CKs")
  public void testSimpleUpdate() {
    updateUser(1, 2, 3, "Tom");
  }

  @Test
  @DisplayName("Should update user with all PKs and not-all CKs")
  public void testUpdateWithoutOneCK() {
    // given
    updateUser(1, 2, 3, "Tom");
    updateUser(1, 2, 4, "Mike");

    // When update for partial PKs, should override two user-names
    updateUserPartialPk(1, 2, "Updated");

    // then
    List<Row> allUsers = findAllUsers(1, 2);
    assertThat(allUsers.get(0).getString("username")).isEqualTo("Updated");
    assertThat(allUsers.get(1).getString("username")).isEqualTo("Updated");
  }

  private void updateUser(int pk1, int cc1, int cc2, String username) {
    Object response =
        CLIENT.executeKeyspaceQuery(
            KEYSPACE,
            String.format(
                "mutation {\n"
                    + "  result: updateUser(user: {pk: %s, cc1: %s, cc2: %s, username: \"%s\"})\n"
                    + "}",
                pk1, cc1, cc2, username));

    // Should have generated an id
    Boolean id = JsonPath.read(response, "$.result");
    assertThat(id).isTrue();
  }

  private void updateUserPartialPk(int pk1, int cc1, String username) {
    Object response =
        CLIENT.executeKeyspaceQuery(
            KEYSPACE,
            String.format(
                "mutation {\n"
                    + "  result: updateUserPartialPk(user: {pk: %s, cc1: %s, username: \"%s\"})\n"
                    + "}",
                pk1, cc1, username));

    // Should have generated an id
    Boolean id = JsonPath.read(response, "$.result");
    assertThat(id).isTrue();
  }
}