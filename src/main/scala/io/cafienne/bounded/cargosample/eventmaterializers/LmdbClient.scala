/*
 * Copyright (C) 2018-2026  Cafienne B.V.
 */

package io.cafienne.bounded.cargosample.eventmaterializers

import java.io.File

import org.lmdbjava.{Env, EnvFlags}
import org.lmdbjava.DbiFlags.MDB_CREATE
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.ByteBuffer.allocateDirect

import org.slf4j.LoggerFactory

trait LmdbClient {

  def put(key: String, value: String): Unit = ???

  def get(key: String): Option[String] = ???

  def delete(key: String): String = ???

}

class CargoLmdbClient(lmdbPath: File) extends LmdbClient {

  private val logger = LoggerFactory.getLogger("CargoLmdbClient")
  private val dbSize = 1000000

  if (!lmdbPath.exists()) {
    val parentFolder = new File(lmdbPath.getParent)
    if (!parentFolder.exists()) {
      if (parentFolder.mkdirs()) {
        logger.debug("Created {} in order to store LMDB files", parentFolder.getAbsolutePath)
      } else {
        logger.debug(
          "Could not create {} in order to store LMDB files, please create this folder by hand and restart",
          parentFolder.getAbsolutePath
        )
      }
    }
  }

  val env = Env.create
    .setMapSize(dbSize)
    .setMaxDbs(1)
    .setMaxReaders(100)
    .open(lmdbPath, EnvFlags.MDB_NOSUBDIR)

  val dbi = env.openDbi(CargoLmdbClient.dbName, MDB_CREATE)

  override def put(key: String, value: String): Unit = {
    val txn = env.txnWrite()
    try {
      val keyByteBuffer = allocateDirect(100)
      keyByteBuffer.put(key.getBytes(UTF_8)).flip

      val byteArr         = value.getBytes(UTF_8)
      val valueByteBuffer = allocateDirect(byteArr.length)
      valueByteBuffer.put(byteArr).flip()
      dbi.put(txn, keyByteBuffer, valueByteBuffer)
      txn.commit()
    } finally {
      txn.close()
    }
  }

  override def get(key: String): Option[String] = {
    val txn = env.txnRead()
    try {
      val keyByteBuffer = allocateDirect(100)
      keyByteBuffer.put(key.getBytes(UTF_8)).flip

      val found = dbi.get(txn, keyByteBuffer)
      if (found != null) {
        val fetchedVal = txn.`val`()
        Some(UTF_8.decode(fetchedVal).toString())
      } else {
        None
      }
    } finally {
      txn.close()
    }
  }

  override def delete(key: String) = {
    throw new IllegalStateException("Not implemented")
  }

}

object CargoLmdbClient {
  val dbName = "cargo"
}
