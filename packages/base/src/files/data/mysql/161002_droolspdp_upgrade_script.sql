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

drop table if exists drools.IntegrityAuditEntity;
create table if not exists drools.IntegrityAuditEntity
(
    id int not null auto_increment, 
    persistenceUnit varchar(100) not null, 
    site varchar(100), 
    nodeType varchar(100), 
    resourceName varchar(100) not null, 
    designated boolean default false, 
    jdbcDriver varchar(100) not null, 
    jdbcUrl varchar(100) not null, 
    jdbcUser varchar(30) not null, 
    jdbcPassword varchar(30) not null, 
    createdDate TIMESTAMP NOT NULL default current_timestamp,
    lastUpdated TIMESTAMP NOT NULL,
    primary key(id)
); 

alter table drools.IntegrityAuditEntity add constraint resourceName_uniq unique(resourceName);

 
