package com.austindata.orders

import scala.io.Source
import com.typesafe.scalalogging.slf4j.Logging
import annotation.tailrec
import java.io.File

import scala.language.postfixOps

/**
 * The Driver for this program searchs for a .csv file in the root directory.  If
 * one is not found, it alerts the user and exits.  If more than one are found it
 * alerts the user and exits.
 */
object Driver extends Logging {
  def main(args: Array[String]) {
    // Retrieve Array of files ending with .csv in the root directory
    val files = new File(".").list.filter(_.endsWith(".csv"))
    // If no .csv files were found, alert the user and exit
    if (files.isEmpty) {
      logger warn ("No .csv File Found!")
      System.exit(0)
    }
    // If multiple .csv files were found, alert the user and exit
    if (files.length > 1) {
      logger warn ("Multiple .csv Files Found!")
      System.exit(0)
    }
    // Process only csv file in the array
    logger info ("Processing CSV File:, " + files(0))
    EnableFallout(files(0))
  }
}

/**
 * Companion object
 */
object EnableFallout {
  /**
   * Format the order number for TIMS by right justifying it.  This is @tailrec optimized recursive method
   *
   * @param the orderNumber without leading or trailing spaces
   */
  @tailrec
  def formatOrderNum(orderNumber: String): String = {
    if (orderNumber.length == 20) orderNumber
    else formatOrderNum(" " + orderNumber)
  }

  def apply(fileName: String) = new EnableFallout(fileName)
}

/**
 * Enable Fallout for orders listed in a CSV file by updating TMOOMST and changing the fallout flag (TMFOFLG) to "Y".
 *
 * If the county keyed in the CSV file is not matched, a warning is generated.  If the
 * order is not found in TMOOMST or the fallout flag (OMFOFLG) is not disabled ("N") a
 * warning is generated.  Finally, if updating the record fails, an error is generated.
 *
 * @param filename the path to the CSV file
 */
class EnableFallout(fileName: String) extends Logging {
  // Create an iterator oof CSVOrder object from the CSV File
  val csvEntries: Iterator[CSVOrder] = Source fromFile (fileName) getLines () map (l => CSVOrder(l))

  for (csvOrder <- csvEntries) {
    csvOrder county match {
      case None => logger error (",Invalid County for Order:, " + csvOrder.order)
      case Some(x) => {
        enableFallout(csvOrder.order, x)
      }
    }
  }

  /**
   * Enable Fallout.  The companyCode is globably defined in the package object becuase this version
   * of the progrm is specific to Gracy Title.
   *
   * @param orderNumber the Order Number
   * @param cntyCode the TIMS County Code
   */
  private def enableFallout(orderNumber: String, cntyCode: String) {
    logger debug (",Attempting to Enable Fallout:, " + orderNumber + "," + companyCode + "," + cntyCode)

    // Format the order number for TIMS
    val fmtOrderNum = EnableFallout.formatOrderNum(orderNumber)

    // Retireve the record for this order from TMOOMST.
    val records = AS400FileHelpers.getKeyedFileRecords(orderFile, libraryPrefix + cntyCode,
      Array(cntyCode, companyCode, fmtOrderNum))

    records match {
      case None => logger error (",Order Not Found:, " + orderNumber + "," + companyCode + "," + cntyCode)
      case Some(listRecords) => {
        for (record <- listRecords) {
          val status = record.getField("OMFOFLG").asInstanceOf[String].toUpperCase.trim

          if (status == "N") { // Attempt to enable fallout
            record.setField("OMFOFLG", "Y")

            val success = AS400FileHelpers.updateKeyedFileRecord(orderFile, libraryPrefix + cntyCode,
              Array(cntyCode, companyCode, fmtOrderNum), record)

            success match { // Error handling in case the update fails
              case None => logger error (",Failed to Update Order:, " + orderNumber + "," + companyCode + "," + cntyCode)
              case Some(x) => logger info (",Enabled Fallout for Order:, " + orderNumber + "," + companyCode + "," + cntyCode)
            }
          } else logger warn (",Fallout Already Enabled:, " + orderNumber + "," + companyCode + "," + cntyCode + "," + status)
        }
      }
    }
  }
}

/**
 * Object representing each line of the CSV file.  The county is stored as an Option in case it is not valid.
 *
 * The Order Number is converted to uppercase since TIMS does not allow lowercase input
 *
 * @param line a Comma Seperated Line: "order number, county name"
 */
case class CSVOrder(line: String) extends Logging {
  val data = line split (",")

  val order = (data(0) trim) toUpperCase
  // Handle case where the county is missing from the CSV file
  val county: Option[String] = {
    if (data.length == 2) getCounty(data(1) trim)
    else None
  }
  /**
   * Replace the keyed county inforamtoin in the CSV file with our county code.
   *
   * Also handle cases where they do use our County Codes
   */
  private def getCounty(county: String): Option[String] = county match {
    case "Hays" => Some("HY")
    case "Travis" => Some("TR")
    case "Williamson" => Some("WM")
    case "HY" => Some("HY")
    case "TR" => Some("TR")
    case "WM" => Some("WM")
    case _ => None
  }
}

