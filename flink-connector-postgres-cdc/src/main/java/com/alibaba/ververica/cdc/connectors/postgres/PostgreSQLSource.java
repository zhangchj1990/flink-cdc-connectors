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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.ververica.cdc.connectors.postgres;

import com.alibaba.ververica.cdc.debezium.DebeziumDeserializationSchema;
import com.alibaba.ververica.cdc.debezium.DebeziumSourceFunction;
import io.debezium.connector.postgresql.PostgresConnector;

import java.util.Properties;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * A builder to build a SourceFunction which can read snapshot and continue to consume binlog for PostgreSQL.
 */
public class PostgreSQLSource {

	public static <T> Builder<T> builder() {
		return new Builder<>();
	}


	/**
	 * Builder class of {@link PostgreSQLSource}.
	 */
	public static class Builder<T> {

		private String pluginName = "decoderbufs";
		private int port = 5432; // default 5432 port
		private String hostname;
		private String database;
		private String username;
		private String password;
		private String[] schemaList;
		private String[] tableList;
		private Properties dbzProperties;
		private DebeziumDeserializationSchema<T> deserializer;

		/**
		 * The name of the Postgres logical decoding plug-in installed on the server.
		 * Supported values are decoderbufs, wal2json, wal2json_rds, wal2json_streaming,
		 * wal2json_rds_streaming and pgoutput.
		 */
		public Builder<T> decodingPluginName(String name) {
			this.pluginName = name;
			return this;
		}

		public Builder<T> hostname(String hostname) {
			this.hostname = hostname;
			return this;
		}

		/**
		 * Integer port number of the PostgreSQL database server.
		 */
		public Builder<T> port(int port) {
			this.port = port;
			return this;
		}

		/**
		 * The name of the PostgreSQL database from which to stream the changes.
		 */
		public Builder<T> database(String database) {
			this.database = database;
			return this;
		}

		/**
		 * An optional list of regular expressions that match schema names to be monitored;
		 * any schema name not included in the whitelist will be excluded from monitoring.
		 * By default all non-system schemas will be monitored.
		 */
		public Builder<T> schemaList(String... schemaList) {
			this.schemaList = schemaList;
			return this;
		}

		/**
		 * An optional list of regular expressions that match fully-qualified table identifiers
		 * for tables to be monitored; any table not included in the whitelist will be excluded
		 * from monitoring. Each identifier is of the form schemaName.tableName.
		 * By default the connector will monitor every non-system table in each monitored schema.
		 */
		public Builder<T> tableList(String... tableList) {
			this.tableList = tableList;
			return this;
		}

		/**
		 * Name of the PostgreSQL database to use when connecting to the PostgreSQL database server.
		 */
		public Builder<T> username(String username) {
			this.username = username;
			return this;
		}

		/**
		 * Password to use when connecting to the PostgreSQL database server.
		 */
		public Builder<T> password(String password) {
			this.password = password;
			return this;
		}

		/**
		 * The Debezium Postgres connector properties.
		 */
		public Builder<T> debeziumProperties(Properties properties) {
			this.dbzProperties = properties;
			return this;
		}

		/**
		 * The deserializer used to convert from consumed {@link org.apache.kafka.connect.source.SourceRecord}.
		 */
		public Builder<T> deserializer(DebeziumDeserializationSchema<T> deserializer) {
			this.deserializer = deserializer;
			return this;
		}

		public DebeziumSourceFunction<T> build() {
			Properties props = new Properties();
			props.setProperty("connector.class", PostgresConnector.class.getCanonicalName());
			props.setProperty("plugin.name", pluginName);
			// hard code server name, because we don't need to distinguish it, docs:
			// Logical name that identifies and provides a namespace for the particular PostgreSQL
			// database server/cluster being monitored. The logical name should be unique across
			// all other connectors, since it is used as a prefix for all Kafka topic names coming
			// from this connector. Only alphanumeric characters and underscores should be used.
			props.setProperty("database.server.name", "postgres-binlog-source");
			props.setProperty("database.hostname", checkNotNull(hostname));
			props.setProperty("database.dbname", checkNotNull(database));
			props.setProperty("database.user", checkNotNull(username));
			props.setProperty("database.password", checkNotNull(password));
			props.setProperty("database.port", String.valueOf(port));

			if (schemaList != null) {
				props.setProperty("schema.whitelist", String.join(",", schemaList));
			}
			if (tableList != null) {
				props.setProperty("table.whitelist", String.join(",", tableList));
			}

			if (dbzProperties != null) {
				dbzProperties.forEach(props::put);
			}

			return new DebeziumSourceFunction<>(
				deserializer,
				props);
		}
	}
}
