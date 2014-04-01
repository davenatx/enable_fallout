enable_fallout
============

This program is designed to enable fallout for orders contained in a CSV file for Gracy Title.
The expected format of the CSV files is "Order Number","County Name".  Fallout is enabled  
in TIMS by updating the OMFOFLG flag to "Y"

Building
--------

This project uses the Simple Build Tool .13 to perform the build and includes the 
sbt-assembly plugin to create one JAR file containing all of the dependencies.  This is 
accomplished by running the assembly task from within SBT.

Overview
--------

In the current workflow, the CSV file is received daily as an e-mail attached.  This 
CSV file is placed in the directory where this program is executed.  When this program is
launched, if a .csv file is not found, a warning is produced and the program exits.  If 
multiple .csv files are found, the program also produces a warning and exits.

Once execution is underway, this program matches the "County Name" supplied in the CSV file
to Austin Data's county codes.  e.g. "Travis County" becomes "TR".  If any county code fails
to match, a warning is logged for this entry.

Before fallout is enabled, this program first checks that it exists in TIMS and verifies 
it is currently enabled.  If the order does not exist or has the fallout flag set to a status 
other than "N", a warning is generated.

When an order exists in TIMS and the fallout flag is "N", it is changed to "Y"

A detailed log file is generated each time the program is run that also contains a timestamp. 
This is intended to provide an audit trail in case there is ever a need to review the history.  

