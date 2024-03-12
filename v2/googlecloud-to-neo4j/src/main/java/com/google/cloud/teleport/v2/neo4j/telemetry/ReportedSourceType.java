/*
 * Copyright (C) 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.neo4j.telemetry;

import com.google.cloud.teleport.v2.neo4j.model.enums.SourceType;
import com.google.cloud.teleport.v2.neo4j.model.job.Source;

public enum ReportedSourceType {
  BIGQUERY {
    @Override
    public String format() {
      return "BigQuery";
    }
  },
  TEXT_INLINE {
    @Override
    public String format() {
      return "Text/Inline";
    }
  },
  TEXT_GCS {
    @Override
    public String format() {
      return "Text/GCS";
    }
  };

  public static ReportedSourceType reportedSourceTypeOf(Source source) {
    SourceType sourceType = source.getSourceType();
    switch (sourceType) {
      case bigquery:
        return BIGQUERY;
      case text:
        if (source.getInline() != null) {
          return TEXT_INLINE;
        }
        return TEXT_GCS;
    }
    throw new IllegalArgumentException(
        String.format(
            "could not determine source type to report from given source type: %s", sourceType));
  }

  @Override
  public String toString() {
    return format();
  }

  public abstract String format();
}
