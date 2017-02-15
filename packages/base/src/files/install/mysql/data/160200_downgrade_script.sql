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
drop table if exists xacml.MicroServiceLocation; 
drop table if exists xacml.DCAEUsers; 
drop table if exists xacml.microserviceconfigname; 
drop table if exists xacml.vmType; 

alter table xacml.term drop column created_by; 
alter table xacml.term drop column created_date; 
alter table xacml.term drop column modified_by; 
alter table xacml.term drop column modified_date;
 
drop table if exists xacml.pepoptions; 
drop table if exists xacml.VarbindDictionary; 
drop table if exists xacml.GocEventAlarm; 
drop table if exists xacml.GocTraversal;
drop table if exists xacml.BRMSParamTemplate;  

alter table xacml.roles drop column name; 

