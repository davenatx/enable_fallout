package com.austindata.orders

import com.typesafe.scalalogging.slf4j.Logging
import com.ibm.as400.access._
import java.math.{ BigDecimal => AS400Decimal }
import collection.mutable.ListBuffer
import annotation.tailrec

/**
 * Basic AS400 Connection Pool
 */
/**TODO** add exception handling and possibly use loan pattern*/
object AS400Pool extends Logging {
  // Lazy to the pool is not created until it is accessed
  lazy val pool = {
    val as400ConnectionPool = new AS400ConnectionPool
    logger.debug("AS400Pool created")
    as400ConnectionPool.fill(system, userProfile, password, AS400.RECORDACCESS, 1)
    as400ConnectionPool
  }
  // Ugly so the log messages occur after the pool has been created
  def getAS400: AS400 = {
    // retrieve connection before reporting statistics
    val con = pool.getConnection(system, userProfile, password)
    logger.trace("Active Connections: " + pool.getActiveConnectionCount(system, userProfile))
    logger.trace("Available Connections: " + pool.getAvailableConnectionCount(system, userProfile))
    con
  }

  def returnAS400(as400: AS400) {
    pool.returnConnectionToPool(as400)
    logger.trace("Active Connections: " + pool.getActiveConnectionCount(system, userProfile))
    logger.trace("Available Connections: " + pool.getAvailableConnectionCount(system, userProfile))

  }
  // Need to explicitly call close on the pool
  def close {
    pool.close()
    logger.debug("AS400Pool closed")
  }
}

/**
 * AS400 File Operations using the LoanPattern.  Each method manages obtaining and returning the AS400 Object as well as the
 * open, close, and exception handling for the AS400 file being processed.
 */
object AS400LoanWrapper extends Logging {

  /**
   * Loan Pattern for Read Only operations on Keyed Files.
   *
   * @param file the Keyed File Name
   * @param library the Library containing the Keyed File
   * @param action the KeyedFile => Option[List[Record] function to retrieve records
   * @retrun Option[List[Record]] the List of Records wrapped as an Option in case none where found or an error occured
   */
  def withReadOnlyKeyedFile(file: String, library: String)(action: KeyedFile => Option[List[Record]]): Option[List[Record]] = {
    val as400 = AS400Pool.getAS400
    val path = new QSYSObjectPathName(library, file, "*FIRST", "MBR")
    val recordDescription = new AS400FileRecordDescription(as400, path.getPath)
    val keyedFile = new KeyedFile(as400, path.getPath)

    try {
      keyedFile.setRecordFormat(recordDescription.retrieveRecordFormat()(0))
      keyedFile.open(AS400File.READ_ONLY, 0, AS400File.COMMIT_LOCK_LEVEL_NONE)

      action(keyedFile)

    } catch {
      case e: Exception => throw e
    } finally {
      if (keyedFile != null) {
        try {
          keyedFile.close()
          AS400Pool.returnAS400(as400)
        } catch {
          case e: Exception => throw e
        }
      }
    }
  }

  /**
   * Loan Pattern for ReadAll operations on Sequential Files.  It assumes the supplied action function uses the realAll method
   * which requires the SequentialFile to remain closed.  This is why SequentialFile.open() is never called
   *
   * @param file the Sequential File Name
   * @param library the Library containing the Sequential File
   * @param action the SequentialFile => Option[List[Record]] function to retrieve the records
   * @retrun Option[List[Record]] the List of Records wrapped as an Option in case none where found or an error occured
   */
  def withReadOnlySequentialFile(file: String, library: String)(action: SequentialFile => Option[List[Record]]): Option[List[Record]] = {
    val as400 = AS400Pool.getAS400
    val path = new QSYSObjectPathName(library, file, "*FIRST", "MBR")
    val recordDescription = new AS400FileRecordDescription(as400, path.getPath)
    val seqFile = new SequentialFile(as400, path.getPath)

    try {
      seqFile.setRecordFormat(recordDescription.retrieveRecordFormat()(0))

      action(seqFile)

    } catch {
      case e: Exception => throw e
    } finally {
      if (seqFile != null) {
        try {
          seqFile.close()
          AS400Pool.returnAS400(as400)
        } catch {
          case e: Exception => throw e
        }
      }
    }
  }

  /**
   * Loan Pattern for Read Write Keyed Files.  The KeyedFile is opened with READ_WRITE so both write and update operations can be
   * performed.
   *
   * @param file the Keyed File Name
   * @param library the Library containing the Keyed File
   * @param action the KeyedFile => Option[Boolean] function to write or update the file
   * @retrun Option[Boolean] the status of the write or update operation wrapped in an Option in case an error occured
   */
  def withReadWriteKeyedFile(file: String, library: String)(action: KeyedFile => Option[Boolean]): Option[Boolean] = {
    val as400 = AS400Pool.getAS400
    val path = new QSYSObjectPathName(library, file, "*FIRST", "MBR")
    val recordDescription = new AS400FileRecordDescription(as400, path.getPath)
    val keyedFile = new KeyedFile(as400, path.getPath)

    try {
      keyedFile.setRecordFormat(recordDescription.retrieveRecordFormat()(0))
      keyedFile.open(AS400File.READ_WRITE, 0, AS400File.COMMIT_LOCK_LEVEL_NONE)

      action(keyedFile)

    } catch {
      case e: Exception => throw e
    } finally {
      if (keyedFile != null) {
        try {
          keyedFile.close()
          AS400Pool.returnAS400(as400)
        } catch {
          case e: Exception => throw e
        }
      }
    }
  }
}

/**
 * High level AS400 File Operations using the LoanWrapper methods.  These methods can be used without worrying about
 * managing the AS400 object or opening or closing the File objects.
 */
object AS400FileHelpers extends Logging {
  /**
   * Retrieve Records matching the key from a Keyed File
   *
   * The call to positionCursorBefore(key) is what allows this to work with binary key fields like TMDFMSTFD.  For some
   * reason if this call is absent, nothing is returned.  I suspect it has to do with the "Read Equal Key Anomaly"
   * outlined by Bob Cozzi in Modern RPG IV Third Edition.
   *
   * @param file the Keyed File Name
   * @param library the Library containing the Keyed File
   * @param key the Key to read the Keyed File with
   * @retrun Option[List[Record]] the List of Records wrapped as an Option in case none where found or an error occured
   */
  def getKeyedFileRecords(file: String, lib: String, key: Array[Object]): Option[List[Record]] = {
    AS400LoanWrapper.withReadOnlyKeyedFile(file, lib) {
      keyedFile =>
        {
          // Handle case where positionCursorBefore returns null
          try {
            keyedFile.positionCursorBefore(key)
            Some(readRecords(keyedFile, key, Nil).reverse)
          } catch {
            case e: Exception => logger.trace(e.getMessage); None
          }
        }
    }
  }

  /**
   * Helper method to recursivly read all the Records matching the Key. This is @tailrec optimized method.
   *
   * @param keyedFile the KeyedFile
   * @param key the Key to read the KeyedFile with
   * @param acc the List[Record] representing the accumulator
   * @retrun List[Record] the records matching the Key
   */
  @tailrec
  private def readRecords(keyedFile: KeyedFile, key: Array[Object], acc: List[Record]): List[Record] = {
    keyedFile.readNextEqual(key) match {
      case null => acc
      case r => readRecords(keyedFile, key, r :: acc)
    }
  }

  /**
   * Read all the Records in a Sequential File.
   *
   * @param file the Sequential File Name
   * @param library the Library containing the Sequential File
   * @retrun Option[List[Record]] the List of Records wrapped as an Option in case none where found or an error occured
   */
  def getSequentialFileRecords(file: String, lib: String): Option[List[Record]] = {
    AS400LoanWrapper.withReadOnlySequentialFile(file, lib) {
      seqFile =>
        {
          val records = seqFile.readAll
          Some(records.toList)
        }
    }
  }

  /**
   * Write a Record to a Keyed File
   *
   * @param file the Keyed File Name
   * @param library the Library containing the Keyed File
   * @param record the Record to write to the Keyed File
   * @retrun Option[Boolean] the status of the write or update operation wrapped in an Option in case an error occured
   */
  def writeKeyedFileRecord(file: String, lib: String, record: Record): Option[Boolean] = {
    AS400LoanWrapper.withReadWriteKeyedFile(file, lib) {
      keyedFile =>
        {
          try {
            keyedFile.write(record)
            Some(true)
          } catch {
            case e: Exception => logger.trace(e.getMessage); None
          }

        }
    }
  }

  /**
   * Update a Record in a Keyed File
   *
   * @param file the Keyed File Name
   * @param library the Library containing the Keyed File
   * @param key the Key to read the Keyed File with
   * @param record the Record to write to the Keyed File
   * @retrun Option[Boolean] the status of the write or update operation wrapped in an Option in case an error occured
   */
  def updateKeyedFileRecord(file: String, lib: String, key: Array[Object], record: Record): Option[Boolean] = {
    AS400LoanWrapper.withReadWriteKeyedFile(file, lib) {
      keyedFile =>
        {
          try {
            keyedFile.update(key, record)
            Some(true)
          } catch {
            case e: Exception => logger.trace(e.getMessage); None
          }
        }
    }
  }
}