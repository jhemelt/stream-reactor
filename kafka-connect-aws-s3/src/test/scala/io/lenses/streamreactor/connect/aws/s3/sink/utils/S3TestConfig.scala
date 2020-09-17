
/*
 * Copyright 2020 Lenses.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.lenses.streamreactor.connect.aws.s3.sink.utils

import java.io._
import java.nio.charset.StandardCharsets
import java.util.Scanner
import java.util.stream.Collectors

import com.google.common.io.ByteStreams
import com.typesafe.scalalogging.LazyLogging
import io.lenses.streamreactor.connect.aws.s3.storage.MultipartBlobStoreStorageInterface
import org.apache.commons.io.FileUtils
import org.jclouds.blobstore.BlobStoreContext
import org.jclouds.io.Payload
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object S3TestPayloadReader extends Matchers {

  def readFileToString(bucketName: String, fileName: String, blobStoreContext: BlobStoreContext): String = {
    try {
      val payload: Payload = S3TestPayloadReader.getPayload(bucketName, fileName, blobStoreContext)
      S3TestPayloadReader.extractPayload(payload)
    } catch {
      case t: Throwable => fail("Unable to read file", t)
    }
  }

  def fileExists(bucketName: String, path: String, blobStoreContext: BlobStoreContext): Boolean = blobStoreContext.getBlobStore.blobExists(bucketName, path)

  def extractPayload(payload: Payload): String = {
    val state = new Scanner(payload.openStream(), StandardCharsets.UTF_8.name).useDelimiter("\\Z").next
    new BufferedReader(new StringReader(state)).lines().collect(Collectors.joining())
  }

  def writeToFile(bucketName: String, fileName: String, blobStoreContext: BlobStoreContext, localFileName: String): Unit = {
    val bytes = readPayload(bucketName, fileName, blobStoreContext)
    FileUtils.writeByteArrayToFile(new File(localFileName), bytes)
  }

  def readPayload(bucketName: String, fileName: String, blobStoreContext: BlobStoreContext): Array[Byte] = {
    toByteArray(getPayload(bucketName, fileName, blobStoreContext))
  }

  def getPayload(bucketName: String, fileName: String, blobStoreContext: BlobStoreContext): Payload = {
    blobStoreContext.getBlobStore.getBlob(bucketName, fileName).getPayload
  }

  def toByteArray(payload: Payload): Array[Byte] = {
    ByteStreams.toByteArray(payload.openStream())
  }

  def copyResourceToBucket(
                            resourceSourceFilename: String,
                            blobStoreContainerName: String,
                            blobStoreTargetFilename: String,
                            blobStoreContext: BlobStoreContext
                          ): String = {


    // somehow having this assert here fixes a test failure.  Go figure.
    require(blobStoreContext.getBlobStore.containerExists(blobStoreContainerName))
    val inputStream = classOf[S3TestConfig].getResourceAsStream(resourceSourceFilename)
    require(inputStream != null)
    val contentLength = inputStream.available()
    blobStoreContext.getBlobStore.putBlob(
      blobStoreContainerName,
      blobStoreContext
        .getBlobStore
        .blobBuilder(blobStoreTargetFilename)
        .payload(inputStream)
        .contentLength(contentLength)
        .build()
    )
  }
}

trait S3TestConfig extends AnyFlatSpec with BeforeAndAfter with LazyLogging {

  protected val proxyContext: S3ProxyContext = new S3ProxyContext()

  implicit val blobStoreContext: BlobStoreContext = proxyContext.createBlobStoreContext
  implicit val storageInterface: MultipartBlobStoreStorageInterface = new MultipartBlobStoreStorageInterface(blobStoreContext)

  val BucketName: String = S3ProxyContext.TestBucket

  before {
    logger.debug("Starting proxy")
    proxyContext.startProxy
    logger.debug("Creating test bucket")
    proxyContext.createTestBucket
  }

  after {
    proxyContext.stopProxy
  }

}
