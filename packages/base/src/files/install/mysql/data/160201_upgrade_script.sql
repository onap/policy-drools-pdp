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

alter table xacml.attribute modify created_date timestamp default current_timestamp;
alter table xacml.obadvice modify created_date timestamp default current_timestamp;
alter table xacml.decisionsettings modify created_date timestamp default current_timestamp;
alter table xacml.actionpolicydict modify created_date timestamp default current_timestamp;
alter table xacml.ecompname modify created_date timestamp default current_timestamp;
alter table xacml.policyentity modify created_date timestamp default current_timestamp;
alter table xacml.configurationdataentity  modify created_date timestamp default current_timestamp;
alter table xacml.policydbdaoentity  modify created_date timestamp default current_timestamp;

alter table xacml.actionpolicydict modify created_date timestamp default current_timestamp;
alter table xacml.vsclaction modify created_date timestamp default current_timestamp;
alter table xacml.vnftype modify created_date timestamp default current_timestamp;

alter table xacml.groupentity modify created_date timestamp default current_timestamp;
alter table xacml.pdpentity modify created_date timestamp default current_timestamp;
alter table xacml.actionbodyentity modify created_date timestamp default current_timestamp;

alter table xacml.term modify created_date timestamp default current_timestamp;
alter table xacml.term modify modified_date timestamp not null; 
alter table xacml.varbinddictionary modify created_date timestamp default current_timestamp;
alter table xacml.pepoptions modify created_date timestamp default current_timestamp;
alter table xacml.goceventalarm modify created_date timestamp default current_timestamp;
alter table xacml.goctraversal modify created_date timestamp default current_timestamp;
alter table xacml.brmsparamtemplate modify created_date timestamp default current_timestamp;
