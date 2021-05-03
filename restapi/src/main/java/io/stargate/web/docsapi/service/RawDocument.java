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
package io.stargate.web.docsapi.service;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.stargate.db.PagingPosition;
import io.stargate.db.PagingPosition.ResumeMode;
import io.stargate.db.datastore.ResultSet;
import io.stargate.db.datastore.Row;
import io.stargate.db.schema.Column;
import java.nio.ByteBuffer;
import java.util.List;

public class RawDocument {

  private final String id;
  private final List<String> docKey;
  private final ResultSet resultSet;
  private final boolean hasNext;
  private final List<Row> rows;

  public RawDocument(
      String id, List<String> docKey, ResultSet resultSet, boolean hasNext, List<Row> rows) {
    this.id = id;
    this.docKey = docKey;
    this.resultSet = resultSet;
    this.hasNext = hasNext;
    this.rows = rows;
  }

  public String id() {
    return id;
  }

  public List<String> key() {
    return docKey;
  }

  public List<Row> rows() {
    return rows;
  }

  public Single<RawDocument> populateFrom(Flowable<RawDocument> docs) {
    return docs.limit(1).map(this::populateFrom).singleElement().toSingle();
  }

  public RawDocument populateFrom(RawDocument doc) {
    if (!id.equals(doc.id)) {
      throw new IllegalStateException(
          String.format("Document ID mismatch. Expecting %s, got %s", id, doc.id));
    }

    // Use query state of current doc, but rows from the other doc
    return new RawDocument(id, docKey, resultSet, hasNext, doc.rows);
  }

  public ByteBuffer makePagingState() {
    if (!hasNext) {
      return null;
    }

    if (rows.isEmpty()) {
      throw new IllegalStateException("Cannot resume paging from an empty document");
    }

    if (docKey.size() > 1) {
      throw new IllegalStateException("Resuming paging from a sub-document is not supported.");
    }

    Row lastRow = rows.get(rows.size() - 1);
    Column keyColumn =
        lastRow.columns().stream()
            .filter(c -> "key".equals(c.name()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Column 'key' not found in document table"));
    ByteBuffer keyValue = lastRow.getBytesUnsafe("key");

    if (keyValue == null) {
      throw new IllegalStateException("Missing document key");
    }

    return resultSet.makePagingState(
        PagingPosition.builder()
            .putCurrentRow(keyColumn, keyValue)
            .resumeFrom(ResumeMode.NEXT_PARTITION)
            .build());
  }
}
