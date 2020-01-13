/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.integration;

import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.jdbc.Config;
import org.apache.iotdb.jdbc.Constant;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;

import static org.junit.Assert.fail;

/**
 * Notice that, all test begins with "IoTDB" is integration test. All test which will start the IoTDB server should be
 * defined as integration test.
 */
public class IoTDBMetadataFetchIT {

  private DatabaseMetaData databaseMetaData;

  private static void insertSQL() throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      String[] insertSqls = new String[]{"SET STORAGE GROUP TO root.ln.wf01.wt01",
          "CREATE TIMESERIES root.ln.wf01.wt01.status WITH DATATYPE = BOOLEAN, ENCODING = PLAIN",
          "CREATE TIMESERIES root.ln.wf01.wt01.temperature WITH DATATYPE = FLOAT, ENCODING = RLE, "
              + "compressor = SNAPPY, MAX_POINT_NUMBER = 3"};

      for (String sql : insertSqls) {
        statement.execute(sql);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.closeStatMonitor();
    EnvironmentUtils.envSetUp();

    insertSQL();
  }

  @After
  public void tearDown() throws Exception {
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void showTimeseriesTest() throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      String[] sqls = new String[]{"show timeseries root.ln.wf01.wt01.status", // full seriesPath
          "show timeseries root.ln", // prefix seriesPath
          "show timeseries root.ln.*.wt01", // seriesPath with stars
          "show timeseries", // the same as root
          "show timeseries root.a.b", // nonexistent timeseries, thus returning ""
      };
      String[] standards = new String[]{
          "root.ln.wf01.wt01.status,root.ln.wf01.wt01,BOOLEAN,PLAIN,UNCOMPRESSED,\n",

          "root.ln.wf01.wt01.status,root.ln.wf01.wt01,BOOLEAN,PLAIN,UNCOMPRESSED,\n"
              + "root.ln.wf01.wt01.temperature,root.ln.wf01.wt01,FLOAT,RLE,SNAPPY,\n",

          "root.ln.wf01.wt01.status,root.ln.wf01.wt01,BOOLEAN,PLAIN,UNCOMPRESSED,\n"
              + "root.ln.wf01.wt01.temperature,root.ln.wf01.wt01,FLOAT,RLE,SNAPPY,\n",

          "root.ln.wf01.wt01.status,root.ln.wf01.wt01,BOOLEAN,PLAIN,UNCOMPRESSED,\n"
                  + "root.ln.wf01.wt01.temperature,root.ln.wf01.wt01,FLOAT,RLE,SNAPPY,\n",

          "",

          ""};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }

  @Test
  public void showStorageGroupTest() throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String[] sqls = new String[]{"show storage group"};
      String[] standards = new String[]{"root.ln.wf01.wt01,\n"};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }

  @Test
  public void databaseMetaDataTest() throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    Connection connection = null;
    try {
      connection = DriverManager
          .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
      databaseMetaData = connection.getMetaData();
      showTimeseriesInJson();

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
  }

  @Test
  public void showVersion() throws SQLException, ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String sql = "show version";
      try {
        boolean hasResultSet = statement.execute(sql);
        if(hasResultSet) {
          try(ResultSet resultSet = statement.getResultSet()) {
            resultSet.next();
            Assert.assertEquals(resultSet.getString(1), IoTDBConstant.VERSION);
          }
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }
    }
  }

  @Test
  public void showDevices() throws SQLException, ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String[] sqls = new String[]{"show devices root.ln"};
      String[] standards = new String[]{"root.ln.wf01.wt01,\n"};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }

  @Test
  public void showChildPaths() throws SQLException, ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String[] sqls = new String[]{"show child paths root.ln"};
      String[] standards = new String[]{"root.ln.wf01,\n"};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }

  @Test
  public void showCountTimeSeries() throws SQLException, ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String[] sqls = new String[]{"COUNT TIMESERIES root.ln"};
      String[] standards = new String[]{"2,\n"};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }

  @Test
  public void showCountTimeSeriesGroupBy() throws SQLException, ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String[] sqls = new String[]{"COUNT TIMESERIES root group by level=1"};
      String[] standards = new String[]{"root.ln,2,\n"};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }

  @Test
  public void showCountNodes() throws SQLException, ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      String[] sqls = new String[]{"COUNT NODES root level=1"};
      String[] standards = new String[]{"1,\n"};
      for (int n = 0; n < sqls.length; n++) {
        String sql = sqls[n];
        String standard = standards[n];
        StringBuilder builder = new StringBuilder();
        try {
          boolean hasResultSet = statement.execute(sql);
          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
              while (resultSet.next()) {
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                  builder.append(resultSet.getString(i)).append(",");
                }
                builder.append("\n");
              }
            }
          }
          Assert.assertEquals(builder.toString(), standard);
        } catch (SQLException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
    }
  }




  /**
   * show metadata in json
   */
  private void showTimeseriesInJson() {
    String metadataInJson = databaseMetaData.toString();
    String standard =
        "===  Timeseries Tree  ===\n"
            + "\n"
            + "{\n"
            + "\t\"root\":{\n"
            + "\t\t\"ln\":{\n"
            + "\t\t\t\"wf01\":{\n"
            + "\t\t\t\t\"wt01\":{\n"
            + "\t\t\t\t\t\"temperature\":{\n"
            + "\t\t\t\t\t\t\"args\":\"{max_point_number=3}\",\n"
            + "\t\t\t\t\t\t\"StorageGroup\":\"root.ln.wf01.wt01\",\n"
            + "\t\t\t\t\t\t\"DataType\":\"FLOAT\",\n"
            + "\t\t\t\t\t\t\"Compressor\":\"SNAPPY\",\n"
            + "\t\t\t\t\t\t\"Encoding\":\"RLE\"\n"
            + "\t\t\t\t\t},\n"
            + "\t\t\t\t\t\"status\":{\n"
            + "\t\t\t\t\t\t\"args\":\"{}\",\n"
            + "\t\t\t\t\t\t\"StorageGroup\":\"root.ln.wf01.wt01\",\n"
            + "\t\t\t\t\t\t\"DataType\":\"BOOLEAN\",\n"
            + "\t\t\t\t\t\t\"Compressor\":\"UNCOMPRESSED\",\n"
            + "\t\t\t\t\t\t\"Encoding\":\"PLAIN\"\n"
            + "\t\t\t\t\t}\n"
            + "\t\t\t\t}\n"
            + "\t\t\t}\n"
            + "\t\t}\n"
            + "\t}\n"
            + "}";

    Assert.assertEquals(standard, metadataInJson);
  }
}
