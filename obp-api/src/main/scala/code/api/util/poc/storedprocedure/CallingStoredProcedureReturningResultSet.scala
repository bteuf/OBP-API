package code.api.util.poc.storedprocedure

import java.sql.{DriverManager, ResultSet, SQLException}

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

object CallingStoredProcedureReturningResultSet extends MdcLoggable {
  lazy val conn = DriverManager.getConnection(dbUrl, mySqlUser, mySqlPassword)
  /**
    * MySQL stored procedure:
    * CREATE DEFINER=`root`@`localhost` PROCEDURE `authuser`(IN rate INT)
    * BEGIN
    *     SELECT * FROM authuser;
    * END
    */
    
  val dbUrl: String = APIUtil.getPropsValue("db.url", "jdbc:mysql://localhost:3306/testdb8?useSSL=false")
  val mySqlUser: String = "YOUR_MSSQL_USER"
  val mySqlPassword: String = "YOUR_MYSQL_PASSWORD"

  def main(args: Array[String]): Unit = {
    try {
      
      val statement = conn.prepareCall("{call authuser(?)}")
      statement.setInt(1, 5)
      var hadResults: Boolean = statement.execute()
      // print headings
      println("| First name | Last name | Email |")
      println("==================================")
      while (hadResults) {
        val resultSet: ResultSet = statement.getResultSet
        // process result set
        while (resultSet.next()) {
          val firstName: String = resultSet.getString("firstname")
          val lastName: String = resultSet.getString("lastname")
          val email: String = resultSet.getString("email")
          println(s"| $firstName | $lastName | $email |")
        }
        hadResults = statement.getMoreResults
      }
      statement.close()
    } catch {
      case ex: SQLException => ex.printStackTrace()
    }
  }

}
