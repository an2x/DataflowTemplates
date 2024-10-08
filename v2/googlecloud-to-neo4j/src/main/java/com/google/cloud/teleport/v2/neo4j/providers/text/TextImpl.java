/*
 * Copyright (C) 2022 Google LLC
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
package com.google.cloud.teleport.v2.neo4j.providers.text;

import com.google.cloud.teleport.v2.neo4j.model.helpers.TargetQuerySpec;
import com.google.cloud.teleport.v2.neo4j.model.helpers.TargetSequence;
import com.google.cloud.teleport.v2.neo4j.model.job.OptionsParams;
import com.google.cloud.teleport.v2.neo4j.model.sources.TextSource;
import com.google.cloud.teleport.v2.neo4j.providers.Provider;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;

/** Provider implementation for reading and writing Text files. */
public class TextImpl implements Provider {

  private final TextSource source;
  private final TargetSequence targetSequence;
  private OptionsParams optionsParams;

  public TextImpl(TextSource source, TargetSequence targetSequence) {
    this.source = source;
    this.targetSequence = targetSequence;
  }

  @Override
  public void configure(OptionsParams optionsParams) {
    this.optionsParams = optionsParams;
  }

  @Override
  public boolean supportsSqlPushDown() {
    return false;
  }

  @Override
  public PTransform<PBegin, PCollection<Row>> querySourceBeamRows(Schema schema) {
    return new TextSourceFileToRow(source, schema);
  }

  @Override
  public PTransform<PBegin, PCollection<Row>> queryTargetBeamRows(TargetQuerySpec targetQuerySpec) {
    return new TextTargetToRow(optionsParams, targetSequence, targetQuerySpec);
  }

  @Override
  public PTransform<PBegin, PCollection<Row>> queryMetadata() {
    return new TextSourceFileMetadataToRow(source);
  }
}
