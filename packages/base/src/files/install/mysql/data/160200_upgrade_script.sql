/*-
 * ============LICENSE_START=======================================================
 * Base Package
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

drop table if exists xacml.DCAEuuid; 
CREATE TABLE xacml.DCAEuuid (
 ID int NOT NULL,
 name VARCHAR(45) NOT NULL,
 description VARCHAR(64)
 ); 

drop table if exists xacml.MicroServiceLocation; 
CREATE TABLE xacml.MicroServiceLocation (
 ID int NOT NULL,
 name VARCHAR(45) NOT NULL,
 description VARCHAR(64)
 ); 

drop table if exists xacml.DCAEUsers; 
CREATE TABLE xacml.DCAEUsers (
 ID int NOT NULL,
 name VARCHAR(45) NOT NULL,
 description VARCHAR(64)
 ); 

drop table if exists xacml.microserviceconfigname; 
CREATE TABLE xacml.microserviceconfigname (
 ID int NOT NULL,
 name VARCHAR(45) NOT NULL,
 description VARCHAR(64)
 ); 

drop table if exists xacml.vmType; 
CREATE TABLE xacml.vmType (
 ID int NOT NULL,
 name VARCHAR(45) NOT NULL,
 description VARCHAR(64)
 ); 
 
alter table xacml.term add created_by varchar(100); 
alter table xacml.term add created_date date; 
alter table xacml.term add modified_by varchar(100); 
alter table xacml.term add modified_date date; 
 
drop table if exists xacml.pepoptions; 
CREATE TABLE xacml.PEPOPTIONS (
ID INT NOT NULL,
PEP_NAME VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(1024),
Actions VARCHAR(1024) NOT NULL,
CREATED_DATE TIMESTAMP NOT NULL,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
);

drop table if exists xacml.VarbindDictionary; 
CREATE TABLE xacml.VarbindDictionary (
Id INT NOT NULL, 
created_by VARCHAR(255) NOT NULL, 
created_date TIMESTAMP, 
modified_by VARCHAR(255) NOT NULL, 
modified_date TIMESTAMP NOT NULL, 
varbind_Description VARCHAR(2048), 
varbind_Name VARCHAR(45) NOT NULL UNIQUE, 
varbind_oid VARCHAR(45) NOT NULL, 
PRIMARY KEY (Id)
); 

drop table if exists xacml.GocEventAlarm; 
CREATE TABLE xacml.GocEventAlarm(
Id INT NOT NULL,
Event VARCHAR(45) NOT NULL,
description VARCHAR(1024),
Alarm VARCHAR(1024) NOT NULL,
CREATED_DATE TIMESTAMP NOT NULL,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
); 

drop table if exists xacml.GocTraversal; 
CREATE TABLE xacml.GocTraversal(
ID INT NOT NULL,
TRAVERSAL VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(45) NULL,
CREATED_DATE TIMESTAMP NOT NULL,
CREATED_BY VARCHAR(45) NOT NULL,
MODIFIED_DATE TIMESTAMP NOT NULL,
MODIFIED_BY VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
); 

drop table if exists xacml.BRMSParamTemplate; 
CREATE TABLE xacml.BRMSParamTemplate(
ID INT NOT NULL,
param_template_name VARCHAR(45) NOT NULL,
DESCRIPTION VARCHAR(45),
rule LONGTEXT NOT NULL,
CREATED_DATE TIMESTAMP NOT NULL,
CREATED_BY  VARCHAR(45) NOT NULL,
PRIMARY KEY(ID)
); 

alter table xacml.roles add name varchar(45);



 
