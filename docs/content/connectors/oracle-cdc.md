# Oracle CDC Connector

The Oracle CDC connector allows for reading snapshot data and incremental data from Oracle database. This document describes how to setup the Oracle CDC connector to run SQL queries against Oracle databases.

Dependencies
------------

In order to setup the Oracle CDC connector, the following table provides dependency information for both projects using a build automation tool (such as Maven or SBT) and SQL Client with SQL JAR bundles.

### Maven dependency

```xml
<dependency>
  <groupId>com.ververica</groupId>
  <artifactId>flink-connector-oracle-cdc</artifactId>
  <!-- the dependency is available only for stable releases. -->
  <version>2.2-SNAPSHOT</version>
</dependency>
```

### SQL Client JAR

**Download link is available only for stable releases.**

Download [flink-sql-connector-oracle-cdc-2.2-SNAPSHOT.jar](https://repo1.maven.org/maven2/com/ververica/flink-sql-connector-oracle-cdc/2.2-SNAPSHOT/flink-sql-connector-oracle-cdc-2.2-SNAPSHOT.jar) and put it under `<FLINK_HOME>/lib/`.

Setup Oracle
----------------
You have to enable log archiving for Oracle database and define an Oracle user with appropriate permissions on all databases that the Debezium Oracle connector monitors.

1. Enable log archiving 

   (1.1). Connect to the database as DBA
    ```shells
    sqlplus sys/password@host:port/SID AS SYSDBA
    ```

   (1.2). Enable log archiving
    ```sql
    alter system set db_recovery_file_dest_size = 10G;
    alter system set db_recovery_file_dest = '/opt/oracle/oradata/recovery_area' scope=spfile;
    shutdown immediate;
    startup mount;
    alter database archivelog;
    alter database open;
   ```
    <b>Notes:</b>

    - Enable log archiving requires database restart, pay attention when try to do it
    - The archived logs will occupy a large amount of disk space, so consider clean the expired logs the periodically
   
    (1.3). Check whether log archiving is enabled
    ```sql
    -- Should now "Database log mode: Archive Mode"
    archive log list;
    ```
   <b>Notes:</b>
   
   Supplemental logging must be enabled for captured tables or the database in order for data changes to capture the <em>before</em> state of changed database rows.
   The following illustrates how to configure this on the table/database level.
   ```sql
   -- Enable supplemental logging for a specific table:
   ALTER TABLE inventory.customers ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
   ```
   ```sql
   -- Enable supplemental logging for database
   ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
   ```

2. Create an Oracle user with permissions

   (2.1). Create Tablespace
   ```sql
   sqlplus sys/password@host:port/SID AS SYSDBA;
     CREATE TABLESPACE logminer_tbs DATAFILE '/opt/oracle/oradata/SID/logminer_tbs.dbf' SIZE 25M REUSE AUTOEXTEND ON MAXSIZE UNLIMITED;
     exit;
   ```

   (2.2). Create a user and grant permissions
   ```sql
   sqlplus sys/password@host:port/SID AS SYSDBA;
     CREATE USER flinkuser IDENTIFIED BY flinkpw DEFAULT TABLESPACE LOGMINER_TBS QUOTA UNLIMITED ON LOGMINER_TBS;
     GRANT CREATE SESSION TO flinkuser;
     GRANT SET CONTAINER TO flinkuser;
     GRANT SELECT ON V_$DATABASE to flinkuser;
     GRANT FLASHBACK ANY TABLE TO flinkuser;
     GRANT SELECT ANY TABLE TO flinkuser;
     GRANT SELECT_CATALOG_ROLE TO flinkuser;
     GRANT EXECUTE_CATALOG_ROLE TO flinkuser;
     GRANT SELECT ANY TRANSACTION TO flinkuser;
     GRANT LOGMINING TO flinkuser;

     GRANT CREATE TABLE TO flinkuser;
     GRANT LOCK ANY TABLE TO flinkuser;
     GRANT ALTER ANY TABLE TO flinkuser;
     GRANT CREATE SEQUENCE TO flinkuser;

     GRANT EXECUTE ON DBMS_LOGMNR TO flinkuser;
     GRANT EXECUTE ON DBMS_LOGMNR_D TO flinkuser;

     GRANT SELECT ON V_$LOG TO flinkuser;
     GRANT SELECT ON V_$LOG_HISTORY TO flinkuser;
     GRANT SELECT ON V_$LOGMNR_LOGS TO flinkuser;
     GRANT SELECT ON V_$LOGMNR_CONTENTS TO flinkuser;
     GRANT SELECT ON V_$LOGMNR_PARAMETERS TO flinkuser;
     GRANT SELECT ON V_$LOGFILE TO flinkuser;
     GRANT SELECT ON V_$ARCHIVED_LOG TO flinkuser;
     GRANT SELECT ON V_$ARCHIVE_DEST_STATUS TO flinkuser;
     exit;
   ```

See more about the [Setting up Oracle](https://debezium.io/documentation/reference/1.5/connectors/oracle.html#setting-up-oracle)


How to create an Oracle CDC table
----------------

The Oracle CDC table can be defined as following:

```sql 
-- register an Oracle table 'products' in Flink SQL
Flink SQL> CREATE TABLE products (
     id INT NOT NULL,
     name STRING,
     description STRING,
     weight DECIMAL(10, 3),
     PRIMARY KEY(id) NOT ENFORCED
     ) WITH (
     'connector' = 'oracle-cdc',
     'hostname' = 'localhost',
     'port' = '1521',
     'username' = 'flinkuser',
     'password' = 'flinkpw',
     'database-name' = 'XE',
     'schema-name' = 'inventory',
     'table-name' = 'products');
  
-- read snapshot and binlogs from products table
Flink SQL> SELECT * FROM products;
```

Connector Options
----------------
<div class="highlight">
<table class="colwidths-auto docutils">
   <thead>
      <tr>
        <th class="text-left" style="width: 25%">Option</th>
        <th class="text-left" style="width: 8%">Required</th>
        <th class="text-left" style="width: 7%">Default</th>
        <th class="text-left" style="width: 10%">Type</th>
        <th class="text-left" style="width: 50%">Description</th>
      </tr>
    </thead>
    <tbody>
    <tr>
      <td>connector</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Specify what connector to use, here should be <code>'oracle-cdc'</code>.</td>
    </tr>
    <tr>
      <td>hostname</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>IP address or hostname of the Oracle database server.</td>
    </tr>
    <tr>
      <td>username</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Name of the Oracle database to use when connecting to the Oracle database server.</td>
    </tr>
    <tr>
      <td>password</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Password to use when connecting to the Oracle database server.</td>
    </tr>
    <tr>
      <td>database-name</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Database name of the Oracle server to monitor.</td>
    </tr>
    <tr>
      <td>schema-name</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Schema name of the Oracle database to monitor.</td>
    </tr>
    <tr>
      <td>table-name</td>
      <td>required</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Table name of the Oracle database to monitor.</td>
    </tr>
    <tr>
      <td>port</td>
      <td>optional</td>
      <td style="word-wrap: break-word;">1521</td>
      <td>Integer</td>
      <td>Integer port number of the Oracle database server.</td>
    </tr>
    <tr>
      <td>scan.startup.mode</td>
      <td>optional</td>
      <td style="word-wrap: break-word;">initial</td>
      <td>String</td>
      <td>Optional startup mode for Oracle CDC consumer, valid enumerations are "initial"
           and "latest-offset". 
           Please see <a href="#startup-reading-position">Startup Reading Position</a>section for more detailed information.</td>
    </tr>  
    <tr>
      <td>debezium.*</td>
      <td>optional</td>
      <td style="word-wrap: break-word;">(none)</td>
      <td>String</td>
      <td>Pass-through Debezium's properties to Debezium Embedded Engine which is used to capture data changes from Oracle server.
          For example: <code>'debezium.snapshot.mode' = 'never'</code>.
          See more about the <a href="https://debezium.io/documentation/reference/1.5/connectors/oracle.html#oracle-connector-properties">Debezium's Oracle Connector properties</a></td> 
     </tr>
    </tbody>
</table>    
</div>

Limitation
--------

### Can't perform checkpoint during scanning snapshot of tables
During scanning snapshot of database tables, since there is no recoverable position, we can't perform checkpoints. In order to not perform checkpoints, Oracle CDC source will keep the checkpoint waiting to timeout. The timeout checkpoint will be recognized as failed checkpoint, by default, this will trigger a failover for the Flink job. So if the database table is large, it is recommended to add following Flink configurations to avoid failover because of the timeout checkpoints:

```
execution.checkpointing.interval: 10min
execution.checkpointing.tolerable-failed-checkpoints: 100
restart-strategy: fixed-delay
restart-strategy.fixed-delay.attempts: 2147483647
```

Available Metadata
----------------

The following format metadata can be exposed as read-only (VIRTUAL) columns in a table definition.

<table class="colwidths-auto docutils">
  <thead>
     <tr>
       <th class="text-left" style="width: 15%">Key</th>
       <th class="text-left" style="width: 30%">DataType</th>
       <th class="text-left" style="width: 55%">Description</th>
     </tr>
  </thead>
  <tbody>
    <tr>
      <td>table_name</td>
      <td>STRING NOT NULL</td>
      <td>Name of the table that contain the row.</td>
    </tr>
    <tr>
      <td>schema_name</td>
      <td>STRING NOT NULL</td>
      <td>Name of the schema that contain the row.</td>
    </tr>
    <tr>
      <td>database_name</td>
      <td>STRING NOT NULL</td>
      <td>Name of the database that contain the row.</td>
    </tr>
    <tr>
      <td>op_ts</td>
      <td>TIMESTAMP_LTZ(3) NOT NULL</td>
      <td>It indicates the time that the change was made in the database. <br>If the record is read from snapshot of the table instead of the change stream, the value is always 0.</td>
    </tr>
  </tbody>
</table>

The extended CREATE TABLE example demonstrates the syntax for exposing these metadata fields:
```sql
CREATE TABLE products (
    db_name STRING METADATA FROM 'database_name' VIRTUAL,
    schema_name STRING METADATA FROM 'schema_name' VIRTUAL, 
    table_name STRING METADATA  FROM 'table_name' VIRTUAL,
    operation_ts TIMESTAMP_LTZ(3) METADATA FROM 'op_ts' VIRTUAL,
    id INT NOT NULL,
    name STRING,
    description STRING,
    weight DECIMAL(10, 3),
    PRIMARY KEY(id) NOT ENFORCED
) WITH (
    'connector' = 'oracle-cdc',
    'hostname' = 'localhost',
    'port' = '1521',
    'username' = 'flinkuser',
    'password' = 'flinkpw',
    'database-name' = 'XE',
    'schema-name' = 'inventory',
    'table-name' = 'products'
);
```

Features
--------

### Exactly-Once Processing

The Oracle CDC connector is a Flink Source connector which will read database snapshot first and then continues to read change events with **exactly-once processing** even failures happen. Please read [How the connector works](https://debezium.io/documentation/reference/1.5/connectors/oracle.html#how-the-oracle-connector-works).

### Startup Reading Position

The config option `scan.startup.mode` specifies the startup mode for Oracle CDC consumer. The valid enumerations are:

- `initial` (default): Performs an initial snapshot on the monitored database tables upon first startup, and continue to read the latest binlog.
- `latest-offset`: Never to perform a snapshot on the monitored database tables upon first startup, just read from
  the change since the connector was started.

_Note: the mechanism of `scan.startup.mode` option relying on Debezium's `snapshot.mode` configuration. So please do not use them together. If you specific both `scan.startup.mode` and `debezium.snapshot.mode` options in the table DDL, it may make `scan.startup.mode` doesn't work._

### Single Thread Reading

The Oracle CDC source can't work in parallel reading, because there is only one task can receive change events.

### DataStream Source

The Oracle CDC connector can also be a DataStream source. You can create a SourceFunction as the following shows:

```java
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import com.ververica.cdc.connectors.oracle.OracleSource;

public class OracleSourceExample {
  public static void main(String[] args) throws Exception {
     SourceFunction<String> sourceFunction = OracleSource.<String>builder()
             .hostname()
             .port(1521)
             .database("XE") // monitor XE database
             .schemaList("inventory") // monitor inventory schema
             .tableList("inventory.products") // monitor products table
             .username("flinkuser")
             .password("flinkpw")
             .deserializer(new JsonDebeziumDeserializationSchema()) // converts SourceRecord to JSON String
             .build();

     StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

     env
        .addSource(sourceFunction)
        .print().setParallelism(1); // use parallelism 1 for sink to keep message ordering   
     
     env.execute();
  }
}
```

**Note:** Please refer [Deserialization](../about.html#deserialization) for more details about the JSON deserialization.

Data Type Mapping
----------------
<div class="wy-table-responsive">
<table class="colwidths-auto docutils">
    <thead>
      <tr>
        <th class="text-left">Oracle type<a href="https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/Data-Types.html"></a></th>
        <th class="text-left">Flink SQL type<a href="{% link dev/table/types.md %}"></a></th>
      </tr>
    </thead>
    <tbody>
    <tr>
      <td>NUMBER(p, s <= 0), p - s < 3
      </td>
      <td>TINYINT</td>
    </tr>
    <tr>
      <td>NUMBER(p, s <= 0), p - s < 5
      </td>
      <td>SMALLINT</td>
    </tr>
    <tr>
      <td>NUMBER(p, s <= 0), p - s < 10
      </td>
      <td>INT</td>
    </tr>
    <tr>
      <td>NUMBER(p, s <= 0), p - s < 19
      </td>
      <td>BIGINT</td>
    </tr>
    <tr>
      <td>NUMBER(p, s <= 0), 19 <= p - s <= 38 <br>
      </td>
      <td>DECIMAL(p - s, 0)</td>
    </tr>
    <tr>
      <td>NUMBER(p, s > 0)
      </td>
      <td>DECIMAL(p, s)</td>
    </tr>
    <tr>
      <td>NUMBER(p, s <= 0), p - s > 38
      </td>
      <td>STRING</td>
    </tr>
    <tr>
      <td> 
        FLOAT<br>
        BINARY_FLOAT
      </td>
      <td>FLOAT</td>
    </tr>
    <tr>
      <td>
        DOUBLE PRECISION<br>
        BINARY_DOUBLE
      </td>
      <td>DOUBLE</td>
    </tr>
    <tr>
      <td>NUMBER(1)</td>
      <td>BOOLEAN</td>
    </tr>
    <tr>
      <td>
        DATE<br>
        TIMESTAMP [(p)]
      </td>
      <td>TIMESTAMP [(p)] [WITHOUT TIMEZONE]</td>
    </tr>
    <tr>
      <td>TIMESTAMP [(p)] WITH TIME ZONE</td>
      <td>TIMESTAMP [(p)] WITH TIME ZONE</td>
    </tr>
    <tr>
      <td>TIMESTAMP [(p)] WITH LOCAL TIME ZONE</td>
      <td>TIMESTAMP_LTZ [(p)]</td>
    </tr>
    <tr>
      <td>
        CHAR(n)<br>
        NCHAR(n)<br>
        NVARCHAR2(n)<br>
        VARCHAR(n)<br>
        VARCHAR2(n)<br>
        CLOB<br>
        NCLOB<br>
        XMLType
      </td>
      <td>STRING</td>
    </tr>
    <tr>
      <td>BLOB<br>
      ROWID
      </td>
      <td>BYTES</td>
    </tr>
    <tr>
      <td>
      INTERVAL DAY TO SECOND<br>
      INTERVAL YEAR TO MONTH
      </td>
      <td>BIGINT</td>
    </tr>
    </tbody>
</table>
</div>
