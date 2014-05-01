/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * This code was generated by https://code.google.com/p/google-apis-client-generator/
 * Modify at your own risk.
 */

package com.google.api.services.genomics.model;

/**
 * A Readset is a collection of Reads.
 *
 * <p> This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Genomics API. For a detailed explanation see:
 * <a href="http://code.google.com/p/google-http-java-client/wiki/JSON">http://code.google.com/p/google-http-java-client/wiki/JSON</a>
 * </p>
 *
 */
@SuppressWarnings("javadoc")
public final class Readset extends com.google.api.client.json.GenericJson {

  /**
   * The date this readset was created.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key @com.google.api.client.json.JsonString
  private java.lang.Long created;

  /**
   * The ID of the dataset this readset belongs to.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String datasetId;

  /**
   * File information from the original BAM import. See the BAM format specification for additional
   * information on each field.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<HeaderSection> fileData;

  static {
    // hack to force ProGuard to consider HeaderSection used, since otherwise it would be stripped out
    // see http://code.google.com/p/google-api-java-client/issues/detail?id=528
    com.google.api.client.util.Data.nullOf(HeaderSection.class);
  }

  /**
   * The readset ID.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String id;

  /**
   * The readset name.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String name;

  /**
   * The number of reads in this readset.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key @com.google.api.client.json.JsonString
  private java.math.BigInteger readCount;

  /**
   * The date this readset was created.
   * @return value or {@code null} for none
   */
  public java.lang.Long getCreated() {
    return created;
  }

  /**
   * The date this readset was created.
   * @param created created or {@code null} for none
   */
  public Readset setCreated(java.lang.Long created) {
    this.created = created;
    return this;
  }

  /**
   * The ID of the dataset this readset belongs to.
   * @return value or {@code null} for none
   */
  public java.lang.String getDatasetId() {
    return datasetId;
  }

  /**
   * The ID of the dataset this readset belongs to.
   * @param datasetId datasetId or {@code null} for none
   */
  public Readset setDatasetId(java.lang.String datasetId) {
    this.datasetId = datasetId;
    return this;
  }

  /**
   * File information from the original BAM import. See the BAM format specification for additional
   * information on each field.
   * @return value or {@code null} for none
   */
  public java.util.List<HeaderSection> getFileData() {
    return fileData;
  }

  /**
   * File information from the original BAM import. See the BAM format specification for additional
   * information on each field.
   * @param fileData fileData or {@code null} for none
   */
  public Readset setFileData(java.util.List<HeaderSection> fileData) {
    this.fileData = fileData;
    return this;
  }

  /**
   * The readset ID.
   * @return value or {@code null} for none
   */
  public java.lang.String getId() {
    return id;
  }

  /**
   * The readset ID.
   * @param id id or {@code null} for none
   */
  public Readset setId(java.lang.String id) {
    this.id = id;
    return this;
  }

  /**
   * The readset name.
   * @return value or {@code null} for none
   */
  public java.lang.String getName() {
    return name;
  }

  /**
   * The readset name.
   * @param name name or {@code null} for none
   */
  public Readset setName(java.lang.String name) {
    this.name = name;
    return this;
  }

  /**
   * The number of reads in this readset.
   * @return value or {@code null} for none
   */
  public java.math.BigInteger getReadCount() {
    return readCount;
  }

  /**
   * The number of reads in this readset.
   * @param readCount readCount or {@code null} for none
   */
  public Readset setReadCount(java.math.BigInteger readCount) {
    this.readCount = readCount;
    return this;
  }

  @Override
  public Readset set(String fieldName, Object value) {
    return (Readset) super.set(fieldName, value);
  }

  @Override
  public Readset clone() {
    return (Readset) super.clone();
  }

}
