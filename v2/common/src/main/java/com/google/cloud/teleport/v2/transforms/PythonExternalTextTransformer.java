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
package com.google.cloud.teleport.v2.transforms;

import com.google.auto.value.AutoValue;
import com.google.cloud.teleport.metadata.TemplateParameter;
import com.google.cloud.teleport.v2.transforms.JavascriptTextTransformer.JavascriptTextTransformerOptions;
import com.google.cloud.teleport.v2.values.FailsafeElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.beam.sdk.coders.RowCoder;
import org.apache.beam.sdk.extensions.python.PythonExternalTransform;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.util.PythonCallableSource;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PythonExternalTextTransformer {

  private static final Logger LOG = LoggerFactory.getLogger(PythonExternalTextTransformer.class);

  public interface PythonExternalTextTransformerOptions extends JavascriptTextTransformerOptions {
    @TemplateParameter.GcsReadFile(
        order = 1,
        optional = true,
        description = "Cloud Storage path to python UDF source",
        helpText =
            "The Cloud Storage path pattern for the Python code containing your user-defined "
                + "functions.",
        example = "gs://your-bucket/your-function.py")
    String getPythonExternalTextTransformGcsPath();

    void setPythonExternalTextTransformGcsPath(String pythonExternalTextTransformerGcsPath);

    @TemplateParameter.Text(
        order = 2,
        optional = true,
        regexes = {"[a-zA-Z0-9_]+"},
        description = "UDF Python Function Name",
        helpText =
            "The name of the function to call from your Python file. Use only letters, digits, and underscores.",
        example = "'transform' or 'transform_udf1'")
    String getPythonExternalTextTransformFunctionName();

    void setPythonExternalTextTransformFunctionName(String pythonTextTransformFunctionName);
  }

  @AutoValue
  public abstract static class FailsafePythonExternalUdf<T>
      extends PTransform<PCollection<Row>, PCollection<Row>> {
    public abstract @Nullable String fileSystemPath();

    public abstract @Nullable String functionName();

    public abstract @Nullable Boolean loggingEnabled();

    public abstract @Nullable Integer reloadIntervalMinutes();

    public abstract TupleTag<FailsafeElement<T, String>> successTag();

    public abstract TupleTag<FailsafeElement<T, String>> failureTag();

    public static <T> Builder<T> newBuilder() {
      return new AutoValue_PythonExternalTextTransformer_FailsafePythonExternalUdf.Builder<>();
    }

    private final Counter successCounter =
        Metrics.counter(FailsafePythonExternalUdf.class, "udf-transform-success-count");

    private final Counter failedCounter =
        Metrics.counter(FailsafePythonExternalUdf.class, "udf-transform-failed-count");

    /** Builder for {@link FailsafePythonExternalUdf}. */
    @AutoValue.Builder
    public abstract static class Builder<T> {
      public abstract Builder<T> setFileSystemPath(@Nullable String fileSystemPath);

      public abstract Builder<T> setFunctionName(@Nullable String functionName);

      public abstract Builder<T> setReloadIntervalMinutes(Integer value);

      public abstract Builder<T> setLoggingEnabled(@Nullable Boolean loggingEnabled);

      public abstract Builder<T> setSuccessTag(TupleTag<FailsafeElement<T, String>> successTag);

      public abstract Builder<T> setFailureTag(TupleTag<FailsafeElement<T, String>> failureTag);

      public abstract FailsafePythonExternalUdf<T> build();
    }

    @Override
    public PCollection<Row> expand(PCollection<Row> elements) {
      return elements.apply(
          PythonExternalTransform.<PCollection<Row>, PCollection<Row>>from("__constructor__")
              .withArgs(
                  PythonCallableSource.of(
                      String.format(
                          "import apache_beam as beam\n"
                              + "import traceback\n"
                              + "from apache_beam.io.filesystems import FileSystems\n"
                              + "from typing import Optional\n"
                              + "from typing import Mapping\n"
                              + "from typing import NamedTuple\n"
                              + "\n"
                              + "class ElementRow(NamedTuple):\n"
                              + "  messageId:  Optional[str]\n"
                              + "  message:    Optional[str]\n"
                              + "  attributes: Mapping[str, str]\n"
                              + "\n"
                              + "class FailsafeRow(NamedTuple):\n"
                              + "  original:      ElementRow\n"
                              + "  transformed:   ElementRow\n"
                              + "  error_message: Optional[str]\n"
                              + "  stack_trace:   Optional[str]\n"
                              + "coders.registry.register_coder(FailsafeRow, coders.RowCoder)\n"
                              + "\n"
                              + "class UdfTransform(beam.PTransform):\n"
                              + "  def expand(self, pcoll):\n"
                              + "    return pcoll | \"applyUDF\" >> beam.ParDo(self.UdfDoFn())\n"
                              + "\n"
                              + "  @beam.typehints.with_output_types(FailsafeRow)\n"
                              + "  class UdfDoFn(beam.DoFn):\n"
                              + "    def __init__(self):\n"
                              + "      self.udf_file = FileSystems.open(\"%s\").read().decode()\n"
                              + "\n"
                              + "    def process(self, elem):\n"
                              + "      try:\n"
                              + "        transformed_message = python_callable.PythonCallableWithSource.load_from_script(\n"
                              + "          self.udf_file, \"%s\")(elem.message)\n"
                              + "        transformed_row = beam.Row(messageId=str(elem.messageId),\n"
                              + "                                   message=str(transformed_message),\n"
                              + "                                   attributes=elem.attributes\n"
                              + "        error_message = \"\"\n"
                              + "        stack_trace = \"\"\n"
                              + "\n"
                              + "      except Exception as e:\n"
                              + "        transformed_row = elem\n"
                              + "        error_message = e\n"
                              + "        stack_trace = traceback.format_exc()\n"
                              + "      yield beam.Row(original=elem, transformed=transformed_row,\n"
                              + "                     error_message=error_message, stack_trace=stack_trace)",
                          fileSystemPath(), functionName())))
              // .withExtraPackages(
              //     List.of("cloudpickle"))
              .withOutputCoder(RowCoder.of(FailsafeRowPythonExternalUdf.FAILSAFE_SCHEMA)));
    }
  }

  public abstract static class FailsafeRowPythonExternalUdf<T>
      extends PTransform<PCollection<PubsubMessage>, PCollection<Row>> {
    public static final Schema ROW_SCHEMA =
        Schema.builder()
            .addNullableStringField("messageId")
            .addNullableStringField("message")
            .addMapField("attributes", Schema.FieldType.STRING, Schema.FieldType.STRING)
            .build();

    public static final Schema FAILSAFE_SCHEMA =
        Schema.builder()
            .addNullableRowField("original", ROW_SCHEMA)
            .addNullableRowField("transformed", ROW_SCHEMA)
            .addNullableStringField("error_message")
            .addNullableStringField("stack_trace")
            .build();

    public static String pubsub = "pubsub";
    public static String string = "string";

    public static <T> MapElements<T, Row> getMappingFunction(String sourceType) {
      if (sourceType == pubsub) {
        return MapElements.into(TypeDescriptor.of(Row.class))
            .via(
                (message) -> {
                  assert message != null;
                  return pubSubMessageToRow((PubsubMessage) message);
                });
      } else if (sourceType == string) {
        return MapElements.into(TypeDescriptor.of(Row.class))
            .via(
                (message) -> {
                  assert message != null;
                  return firestoreMessageToRow((String) message);
                });
      } else {
        throw new IllegalStateException();
      }
    }

    public static Row pubSubMessageToRow(PubsubMessage message) {
      String messageId = message.getMessageId();
      String payload = new String(message.getPayload());
      Map<String, String> attributeMap = new HashMap<>();
      if (message.getAttributeMap() != null) {
        attributeMap.putAll(message.getAttributeMap());
      }

      // assert payload != null;
      // assert attributeMap != null;
      Map<String, Object> rowValuesMap = new HashMap<>();
      rowValuesMap.put("messageId", messageId);
      rowValuesMap.put("message", payload);
      rowValuesMap.put("attributes", attributeMap);
      return Row.withSchema(ROW_SCHEMA).withFieldValues(rowValuesMap).build();

      //      return Row.withSchema(FAILSAFE_SCHEMA)
      //          .withFieldValues(
      //              Map.of(
      //                  "original", original_row))
      //          .build();
    }

    public static Row firestoreMessageToRow(String message) {
      Map<String, Object> rowValuesMap = new HashMap<>();
      rowValuesMap.put("message", message);
      return Row.withSchema(ROW_SCHEMA).withFieldValues(rowValuesMap).build();
    }
  }
}
