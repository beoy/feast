/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2018-2020 The Feast Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feast.core.model;

import static feast.proto.core.DataSourceProto.DataSource.SourceType.*;

import feast.core.util.TypeConversion;
import feast.proto.core.DataSourceProto;
import feast.proto.core.DataSourceProto.DataSource.BigQueryOptions;
import feast.proto.core.DataSourceProto.DataSource.FileOptions;
import feast.proto.core.DataSourceProto.DataSource.KafkaOptions;
import feast.proto.core.DataSourceProto.DataSource.KinesisOptions;
import feast.proto.core.DataSourceProto.DataSource.SourceType;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter(AccessLevel.PRIVATE)
@Table(name = "data_sources")
public class DataSource {
  @Column(name = "id")
  @Id
  @GeneratedValue
  private long id;

  // Type of this Data Source
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private SourceType type;

  // DataSource Options
  @Column(name = "config")
  private String configJSON;

  // Field mapping between sourced fields (key) and feature fields (value).
  // Stored as serialized JSON string.
  @Column(name = "field_mapping", columnDefinition = "text")
  private String fieldMapJSON;

  @Column(name = "timestamp_column")
  private String timestampColumn;

  @Column(name = "date_partition_column")
  private String datePartitionColumn;

  public DataSource() {};

  public DataSource(SourceType type) {
    this.type = type;
  }

  /**
   * Construct a DataSource from the given Protobuf representation spec
   *
   * @param spec Protobuf representation of DataSource to construct from.
   * @throws IllegalArgumentException when provided with a invalid Protobuf spec
   * @throws UnsupportedOperationException if source type is unsupported.
   */
  public static DataSource fromProto(DataSourceProto.DataSource spec) {
    DataSource source = new DataSource(spec.getType());
    // Copy source type specific options
    Map<String, String> dataSourceConfigMap = new HashMap<>();
    switch (spec.getType()) {
      case BATCH_FILE:
        dataSourceConfigMap.put("file_url", spec.getFileOptions().getFileUrl());
        dataSourceConfigMap.put("file_format", spec.getFileOptions().getFileFormat());
        break;
      case BATCH_BIGQUERY:
        dataSourceConfigMap.put("table_ref", spec.getBigqueryOptions().getTableRef());
        break;
      case STREAM_KAFKA:
        dataSourceConfigMap.put("bootstrap_servers", spec.getKafkaOptions().getBootstrapServers());
        dataSourceConfigMap.put("class_path", spec.getKafkaOptions().getClassPath());
        dataSourceConfigMap.put("topic", spec.getKafkaOptions().getTopic());
        break;
      case STREAM_KINESIS:
        dataSourceConfigMap.put("class_path", spec.getKinesisOptions().getClassPath());
        dataSourceConfigMap.put("region", spec.getKinesisOptions().getRegion());
        dataSourceConfigMap.put("stream_name", spec.getKinesisOptions().getStreamName());
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Feature Store Type: %s", spec.getType()));
    }

    // Store DataSource mapping as serialised JSON
    source.setConfigJSON(TypeConversion.convertMapToJsonString(dataSourceConfigMap));

    // Store field mapping as serialised JSON
    source.setFieldMapJSON(TypeConversion.convertMapToJsonString(spec.getFieldMappingMap()));

    // Set timestamp mapping columns
    source.setTimestampColumn(spec.getTimestampColumn());
    source.setDatePartitionColumn(spec.getDatePartitionColumn());

    return source;
  }

  /** Convert this DataSource to its Protobuf representation. */
  public DataSourceProto.DataSource toProto() {
    DataSourceProto.DataSource.Builder spec = DataSourceProto.DataSource.newBuilder();
    spec.setType(getType());

    // Extract source type specific options
    Map<String, String> dataSourceConfigMap =
        TypeConversion.convertJsonStringToMap(getConfigJSON());
    switch (getType()) {
      case BATCH_FILE:
        FileOptions.Builder fileOptions = FileOptions.newBuilder();
        fileOptions.setFileUrl(dataSourceConfigMap.get("file_url"));
        fileOptions.setFileFormat(dataSourceConfigMap.get("file_format"));
        spec.setFileOptions(fileOptions.build());
        break;
      case BATCH_BIGQUERY:
        BigQueryOptions.Builder bigQueryOptions = BigQueryOptions.newBuilder();
        bigQueryOptions.setTableRef(dataSourceConfigMap.get("table_ref"));
        spec.setBigqueryOptions(bigQueryOptions.build());
        break;
      case STREAM_KAFKA:
        KafkaOptions.Builder kafkaOptions = KafkaOptions.newBuilder();
        kafkaOptions.setBootstrapServers(dataSourceConfigMap.get("bootstrap_servers"));
        kafkaOptions.setClassPath(dataSourceConfigMap.get("class_path"));
        kafkaOptions.setTopic(dataSourceConfigMap.get("topic"));
        spec.setKafkaOptions(kafkaOptions.build());
        break;
      case STREAM_KINESIS:
        KinesisOptions.Builder kinesisOptions = KinesisOptions.newBuilder();
        kinesisOptions.setClassPath(dataSourceConfigMap.get("class_path"));
        kinesisOptions.setRegion(dataSourceConfigMap.get("region"));
        kinesisOptions.setStreamName(dataSourceConfigMap.get("stream_name"));
        spec.setKinesisOptions(kinesisOptions.build());
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Feature Store Type: %s", getType()));
    }

    // Parse field mapping and options from JSON
    spec.putAllFieldMapping(TypeConversion.convertJsonStringToMap(getFieldMapJSON()));

    spec.setTimestampColumn(getTimestampColumn());
    spec.setDatePartitionColumn(getDatePartitionColumn());

    return spec.build();
  }

  public Map<String, String> getFieldsMap() {
    return TypeConversion.convertJsonStringToMap(getFieldMapJSON());
  }

  @Override
  public int hashCode() {
    return toProto().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DataSource other = (DataSource) o;
    return this.toProto().equals(other.toProto());
  }
}
